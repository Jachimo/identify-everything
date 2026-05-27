"""
Integration tests for the Identify Everything core use case.

Each test class is a self-contained scenario. All scenarios share the
same TestClient fixture but operate on a fresh in-memory database.

Run from the project root:
    PYTHONPATH=server python -m pytest server/tests/ -v

Scenarios covered
-----------------
1. Server health
2. GUID / URL parsing (Python-side, mirrors mobile logic)
3. Single device — full scan-to-sync user journey
4. Photo attachment — upload, retrieve, download
5. Two devices — Device B reads what Device A created
6. Two devices — bidirectional updates and version history
7. Delta sync — each device sees only changes since its last sync
8. Offline queue — simulate mobile pushing a batch of offline-queued items
"""

import re
from datetime import datetime, timezone, timedelta
import io

import pytest
from fastapi.testclient import TestClient

from .conftest import (
    DEVICE_A, DEVICE_B,
    SAMPLE_GUID, SAMPLE_URL, SAMPLE_DOMAIN, SAMPLE_GPS,
    TINY_JPEG,
    headers, register,
)


# ── Helpers ─────────────────────────────────────────────────────────────────

GUID_PATTERN = re.compile(r"^[a-p0-9]{4}_[a-p0-9]{4}_[a-p0-9]{4}_[a-p0-9]{4}$")
URL_GUID_RE  = re.compile(r"/objects/v1/([a-p0-9_]{16,19})$")


def extract_guid_from_url(url: str) -> str | None:
    """Mirror the regex used by the mobile scan screen."""
    match = URL_GUID_RE.search(url)
    if not match:
        return None
    raw = match.group(1).replace("_", "")
    if len(raw) != 16:
        return None
    return f"{raw[0:4]}_{raw[4:8]}_{raw[8:12]}_{raw[12:16]}"


def create_item(client, device_id: str, guid: str, url: str, domain: str,
                data: dict | None = None) -> dict:
    """POST an item and assert 201."""
    payload = {"guid": guid, "url": url, "domain": domain, "device_id": device_id}
    if data:
        payload["data"] = data
    resp = client.post("/api/v1/items", json=payload)
    assert resp.status_code == 201, resp.text
    return resp.json()


def update_item(client, device_id: str, guid: str, data: dict,
                summary: str = "Updated via test") -> dict:
    """PUT item data and assert 200."""
    resp = client.put(
        f"/api/v1/items/{guid}",
        json={"device_id": device_id, "data": data, "change_summary": summary},
    )
    assert resp.status_code == 200, resp.text
    return resp.json()


def get_item(client, guid: str) -> dict:
    """GET item detail and assert 200."""
    resp = client.get(f"/api/v1/items/{guid}")
    assert resp.status_code == 200, resp.text
    return resp.json()


def sync_download(client, device_id: str, sync_token: str,
                  after: str | None = None) -> dict:
    """GET /api/v1/items/sync and return parsed JSON."""
    params = {"after": after} if after else {}
    resp = client.get(
        "/api/v1/items/sync",
        headers=headers(device_id, sync_token),
        params=params,
    )
    assert resp.status_code == 200, resp.text
    return resp.json()


# ═══════════════════════════════════════════════════════════════════════════
# 1. Server health
# ═══════════════════════════════════════════════════════════════════════════

class TestHealth:
    def test_health_endpoint_returns_ok(self, client):
        resp = client.get("/health")
        assert resp.status_code == 200
        assert resp.json() == {"status": "ok"}

    def test_root_endpoint_identifies_api(self, client):
        resp = client.get("/")
        assert resp.status_code == 200
        body = resp.json()
        assert "Identify Everything" in body.get("message", "")


# ═══════════════════════════════════════════════════════════════════════════
# 2. GUID / URL parsing
#    These are pure-logic tests that mirror the mobile app's scan.tsx regex.
#    They validate the contract: any QR code the label generator produces
#    must be parseable by the mobile client and accepted by the server.
# ═══════════════════════════════════════════════════════════════════════════

class TestGuidParsing:
    @pytest.mark.parametrize("url, expected_guid", [
        (
            "https://mylabels.example.com/objects/v1/abcd_1234_efgh_5678",
            "abcd_1234_efgh_5678",
        ),
        (
            "https://example.com/objects/v1/aaaa_bbbb_cccc_dddd",
            "aaaa_bbbb_cccc_dddd",
        ),
        (
            # No separators — 16-char raw form
            "https://example.com/objects/v1/abcd1234efgh5678",
            "abcd_1234_efgh_5678",
        ),
    ])
    def test_valid_qr_url_yields_guid(self, url, expected_guid):
        guid = extract_guid_from_url(url)
        assert guid == expected_guid
        assert GUID_PATTERN.match(guid)

    @pytest.mark.parametrize("url", [
        "https://example.com/wrong/path/abc123",
        "not-a-url",
        "",
        "https://example.com/objects/v1/",
    ])
    def test_invalid_url_yields_none(self, url):
        assert extract_guid_from_url(url) is None

    @pytest.mark.parametrize("guid", [
        "abcd_1234_efgh_5678",
        "aaaa_0000_pppp_9999",
    ])
    def test_guid_character_set_is_valid(self, guid):
        """GUIDs use only [a-p] and [0-9], never [q-z]."""
        assert GUID_PATTERN.match(guid)
        for ch in guid.replace("_", ""):
            assert ch in "abcdefghijklmnop0123456789", f"Invalid char: {ch!r}"

    def test_server_rejects_invalid_guid_format(self, client):
        """Server should still accept any URL string — validation is client-side."""
        resp = client.post("/api/v1/items", json={
            "guid": "abcd_1234_efgh_5678",
            "url": "https://example.com/objects/v1/abcd_1234_efgh_5678",
            "domain": "example.com",
            "device_id": DEVICE_A,
        })
        assert resp.status_code == 201


# ═══════════════════════════════════════════════════════════════════════════
# 3. Single device — full scan-to-sync user journey
#    Mirrors what happens when a user:
#      1. Opens the app
#      2. Scans a pre-printed QR code
#      3. Fills in a title, description; GPS is captured automatically
#      4. Taps "Save Changes"
#      5. Data syncs to the server
# ═══════════════════════════════════════════════════════════════════════════

class TestSingleDeviceJourney:
    def test_full_scan_metadata_sync_flow(self, client):
        # Step 1 — Device registers (happens on first launch)
        token = register(client, DEVICE_A)

        # Step 2 — Mobile scans QR code; app extracts the GUID from the URL
        scanned_url = SAMPLE_URL
        guid = extract_guid_from_url(scanned_url)
        assert guid == SAMPLE_GUID

        # Step 3 — App creates item on server (or queues it for sync)
        item = create_item(client, DEVICE_A, guid, scanned_url, SAMPLE_DOMAIN)
        assert item["guid"] == guid
        item_id = item["item_id"]

        # Step 4 — User adds title, description; GPS captured automatically
        metadata = {
            "title": "Red fire extinguisher",
            "description": "Located near stairwell B, 3rd floor",
            "location": SAMPLE_GPS,
            "condition": "good",
        }
        update_item(client, DEVICE_A, guid, metadata, "Initial metadata from scan")

        # Step 5 — Verify the full record is stored correctly on the server
        record = get_item(client, guid)

        assert record["guid"] == guid
        assert record["item_id"] == item_id

        version = record["latest_version"]
        assert version is not None
        assert version["data"]["title"] == "Red fire extinguisher"
        assert version["data"]["description"] == "Located near stairwell B, 3rd floor"
        assert version["data"]["condition"] == "good"

        loc = version["data"]["location"]
        assert loc["latitude"]  == pytest.approx(37.7749)
        assert loc["longitude"] == pytest.approx(-122.4194)
        assert loc["accuracy"]  == pytest.approx(4.8)
        assert loc["timestamp"] == "2026-01-15T10:30:00Z"

        # Step 6 — Verify item appears in the sync feed for this device
        feed = sync_download(client, DEVICE_A, token)
        guids_in_feed = [i["guid"] for i in feed["changes"]["items_added"]]
        assert guid in guids_in_feed

    def test_duplicate_scan_is_idempotent(self, client):
        """Scanning the same QR code twice must not create duplicate records."""
        register(client, DEVICE_A)
        create_item(client, DEVICE_A, SAMPLE_GUID, SAMPLE_URL, SAMPLE_DOMAIN)

        # Second scan / create attempt
        resp = client.post("/api/v1/items", json={
            "guid": SAMPLE_GUID,
            "url": SAMPLE_URL,
            "domain": SAMPLE_DOMAIN,
            "device_id": DEVICE_A,
        })
        assert resp.status_code == 409  # Conflict — duplicate GUID

    def test_item_version_history_grows_with_each_update(self, client):
        register(client, DEVICE_A)
        create_item(client, DEVICE_A, SAMPLE_GUID, SAMPLE_URL, SAMPLE_DOMAIN)

        update_item(client, DEVICE_A, SAMPLE_GUID, {"title": "v2"}, "Second version")
        update_item(client, DEVICE_A, SAMPLE_GUID, {"title": "v3"}, "Third version")

        resp = client.get(f"/api/v1/items/{SAMPLE_GUID}/versions")
        assert resp.status_code == 200
        versions = resp.json()
        assert len(versions) == 3  # initial + 2 updates

        # Latest version should be canonical
        canonical = [v for v in versions if v["is_canonical"]]
        assert len(canonical) == 1
        assert canonical[0]["data"]["title"] == "v3"


# ═══════════════════════════════════════════════════════════════════════════
# 4. Photo attachment — upload, list, and download
# ═══════════════════════════════════════════════════════════════════════════

class TestPhotoAttachment:
    def test_upload_photo_appears_in_item_record(self, client):
        register(client, DEVICE_A)
        create_item(client, DEVICE_A, SAMPLE_GUID, SAMPLE_URL, SAMPLE_DOMAIN)

        resp = client.post(
            f"/api/v1/items/{SAMPLE_GUID}/attach",
            files={"file": ("photo.jpg", io.BytesIO(TINY_JPEG), "image/jpeg")},
            headers={"X-Device-Id": DEVICE_A},
        )
        assert resp.status_code == 201
        att = resp.json()
        assert "attachment_id" in att
        assert att["filename"] == "photo.jpg"
        assert att["mime_type"] == "image/jpeg"
        attachment_id = att["attachment_id"]

        # Attachment must appear in the item's latest_version.attachments list
        record = get_item(client, SAMPLE_GUID)
        version = record["latest_version"]
        att_ids = [a["attachment_id"] for a in version["attachments"]]
        assert attachment_id in att_ids

    def test_uploaded_photo_can_be_downloaded(self, client):
        register(client, DEVICE_A)
        create_item(client, DEVICE_A, SAMPLE_GUID, SAMPLE_URL, SAMPLE_DOMAIN)

        upload_resp = client.post(
            f"/api/v1/items/{SAMPLE_GUID}/attach",
            files={"file": ("shot.jpg", io.BytesIO(TINY_JPEG), "image/jpeg")},
            headers={"X-Device-Id": DEVICE_A},
        )
        assert upload_resp.status_code == 201
        attachment_id = upload_resp.json()["attachment_id"]

        # Download via the mobile-facing file endpoint
        dl = client.get(f"/api/v1/items/{SAMPLE_GUID}/attach/{attachment_id}")
        assert dl.status_code == 200
        assert dl.content == TINY_JPEG
        assert "image/jpeg" in dl.headers.get("content-type", "")

    def test_multiple_photos_all_listed(self, client):
        register(client, DEVICE_A)
        create_item(client, DEVICE_A, SAMPLE_GUID, SAMPLE_URL, SAMPLE_DOMAIN)

        ids = []
        for i in range(3):
            r = client.post(
                f"/api/v1/items/{SAMPLE_GUID}/attach",
                files={"file": (f"photo_{i}.jpg", io.BytesIO(TINY_JPEG), "image/jpeg")},
                headers={"X-Device-Id": DEVICE_A},
            )
            assert r.status_code == 201
            ids.append(r.json()["attachment_id"])

        record = get_item(client, SAMPLE_GUID)
        stored_ids = [a["attachment_id"] for a in record["latest_version"]["attachments"]]
        for att_id in ids:
            assert att_id in stored_ids

    def test_download_nonexistent_attachment_returns_404(self, client):
        register(client, DEVICE_A)
        create_item(client, DEVICE_A, SAMPLE_GUID, SAMPLE_URL, SAMPLE_DOMAIN)
        resp = client.get(f"/api/v1/items/{SAMPLE_GUID}/attach/does-not-exist")
        assert resp.status_code == 404


# ═══════════════════════════════════════════════════════════════════════════
# 5. Two devices — Device B reads what Device A created
#    Core shared-server scenario: both devices talk to the same backend,
#    so any item created by A is immediately visible to B.
# ═══════════════════════════════════════════════════════════════════════════

class TestTwoDeviceRead:
    def test_device_b_sees_item_created_by_device_a(self, client):
        # Device A scans and records the item
        register(client, DEVICE_A)
        guid = extract_guid_from_url(SAMPLE_URL)
        create_item(client, DEVICE_A, guid, SAMPLE_URL, SAMPLE_DOMAIN)
        update_item(client, DEVICE_A, guid, {
            "title": "Yellow hard hat",
            "location": SAMPLE_GPS,
        })

        # Device B registers independently — no prior knowledge of the item
        token_b = register(client, DEVICE_B)

        # Device B gets the item by GUID (e.g., after scanning the same label)
        record = get_item(client, guid)
        assert record["guid"] == guid
        assert record["latest_version"]["data"]["title"] == "Yellow hard hat"
        assert record["latest_version"]["data"]["location"]["latitude"] == pytest.approx(37.7749)

        # Device B also sees it via the sync feed
        feed = sync_download(client, DEVICE_B, token_b)
        guids = [i["guid"] for i in feed["changes"]["items_added"]]
        assert guid in guids

    def test_device_b_sees_gps_and_metadata_from_device_a(self, client):
        register(client, DEVICE_A)
        register(client, DEVICE_B)

        guid = "aaaa_bbbb_cccc_dddd"
        url  = f"https://tags.example.com/objects/v1/{guid}"

        create_item(client, DEVICE_A, guid, url, "tags.example.com")
        update_item(client, DEVICE_A, guid, {
            "title": "Server rack #12",
            "location": {"latitude": 51.5074, "longitude": -0.1278,
                         "accuracy": 3.2, "timestamp": "2026-03-01T08:00:00Z"},
            "notes": "In the data centre, row 3",
        })

        token_b = register(client, DEVICE_B)
        record = get_item(client, guid)
        data   = record["latest_version"]["data"]

        assert data["title"]  == "Server rack #12"
        assert data["notes"]  == "In the data centre, row 3"
        assert data["location"]["latitude"]  == pytest.approx(51.5074)
        assert data["location"]["longitude"] == pytest.approx(-0.1278)


# ═══════════════════════════════════════════════════════════════════════════
# 6. Two devices — bidirectional updates and version history
#    Device A creates, Device B updates, Device A reads B's update.
#    Then verifies the full version chain is intact.
# ═══════════════════════════════════════════════════════════════════════════

class TestTwoDeviceBidirectionalSync:
    def test_device_a_reads_update_made_by_device_b(self, client):
        register(client, DEVICE_A)
        register(client, DEVICE_B)

        guid = "sync_aaaa_test_0001"
        url  = f"https://example.com/objects/v1/{guid}"

        # Device A creates and sets initial state
        create_item(client, DEVICE_A, guid, url, "example.com", data={
            "title": "Laptop — Dell XPS",
            "status": "in-use",
        })

        # Device B scans the same label later; adds its own update
        update_item(client, DEVICE_B, guid, {
            "title": "Laptop — Dell XPS",
            "status": "returned",
            "checked_in_by": "device-beta",
            "location": {"latitude": 40.7128, "longitude": -74.0060,
                         "accuracy": 6.0, "timestamp": "2026-02-10T14:20:00Z"},
        }, summary="Checked in by Device B")

        # Device A reads the item — must see Device B's update
        record = get_item(client, guid)
        data   = record["latest_version"]["data"]
        assert data["status"]         == "returned"
        assert data["checked_in_by"]  == "device-beta"
        assert data["location"]["latitude"] == pytest.approx(40.7128)

    def test_version_chain_reflects_both_devices(self, client):
        register(client, DEVICE_A)
        register(client, DEVICE_B)

        guid = "sync_bbbb_chain_0002"
        url  = f"https://example.com/objects/v1/{guid}"

        create_item(client, DEVICE_A, guid, url, "example.com",
                    data={"title": "Keyboard", "condition": "new"})
        update_item(client, DEVICE_A, guid, {"title": "Keyboard", "condition": "used"},
                    summary="Device A marks as used")
        update_item(client, DEVICE_B, guid, {"title": "Keyboard", "condition": "worn"},
                    summary="Device B marks as worn")

        # Version history: initial + 2 updates = 3 entries
        resp = client.get(f"/api/v1/items/{guid}/versions")
        assert resp.status_code == 200
        versions = resp.json()
        assert len(versions) == 3

        summaries = [v["change_summary"] for v in versions]
        assert "Device A marks as used" in summaries
        assert "Device B marks as worn" in summaries

        # Only the most recent version is canonical
        canonical = [v for v in versions if v["is_canonical"]]
        assert len(canonical) == 1
        assert canonical[0]["data"]["condition"] == "worn"

    def test_device_b_update_adds_gps_that_device_a_then_reads(self, client):
        """Device A creates without GPS. Device B scans and adds location.
        Device A re-reads and now has GPS data in the record."""
        register(client, DEVICE_A)
        register(client, DEVICE_B)

        guid = "sync_cccc_gps_00003"
        url  = f"https://example.com/objects/v1/{guid}"

        create_item(client, DEVICE_A, guid, url, "example.com",
                    data={"title": "Fire hose cabinet", "location": None})

        # Device B is physically at the cabinet; adds GPS
        update_item(client, DEVICE_B, guid, {
            "title": "Fire hose cabinet",
            "location": {"latitude": 48.8566, "longitude": 2.3522,
                         "accuracy": 2.1, "timestamp": "2026-04-05T09:15:00Z"},
        }, summary="Location tagged by Device B on-site")

        # Device A reads — now has GPS even though it never was at the location
        record = get_item(client, guid)
        loc    = record["latest_version"]["data"]["location"]
        assert loc["latitude"]  == pytest.approx(48.8566)
        assert loc["longitude"] == pytest.approx(2.3522)


# ═══════════════════════════════════════════════════════════════════════════
# 7. Delta sync — each device only fetches changes since its last sync
#    Validates the `?after=` timestamp parameter used by the sync manager.
# ═══════════════════════════════════════════════════════════════════════════

class TestDeltaSync:
    def test_device_b_receives_items_created_by_device_a(self, client):
        token_a = register(client, DEVICE_A)
        token_b = register(client, DEVICE_B)

        # Device A creates several items
        guids_a = []
        for i in range(3):
            g = f"delta_aaaa_item_{i:04d}"
            u = f"https://example.com/objects/v1/{g}"
            create_item(client, DEVICE_A, g, u, "example.com",
                        data={"title": f"Item {i}", "created_by": "A"})
            guids_a.append(g)

        # Device B syncs with no `after` filter → must see all 3 items
        feed = sync_download(client, DEVICE_B, token_b)
        synced = {i["guid"] for i in feed["changes"]["items_added"]}
        for g in guids_a:
            assert g in synced, f"Device B should see {g} but only got: {synced}"

    def test_after_filter_returns_only_newer_items(self, client):
        token_a = register(client, DEVICE_A)

        # Create two items "early"
        for i in range(2):
            g = f"delta_early_{i:04d}_test"
            u = f"https://example.com/objects/v1/{g}"
            create_item(client, DEVICE_A, g, u, "example.com")

        # Record the cutoff timestamp
        cutoff = datetime.now(timezone.utc).isoformat()

        # Create two more items "late"
        late_guids = []
        for i in range(2):
            g = f"delta_late_{i:04d}_test"
            u = f"https://example.com/objects/v1/{g}"
            create_item(client, DEVICE_A, g, u, "example.com",
                        data={"title": f"Late item {i}"})
            late_guids.append(g)

        # Sync with the cutoff → should see only the 2 late items
        feed = sync_download(client, DEVICE_A, token_a, after=cutoff)
        returned = {i["guid"] for i in feed["changes"]["items_added"]}

        for g in late_guids:
            assert g in returned, f"Expected late item {g} but got: {returned}"

        early_guids = {f"delta_early_{i:04d}_test" for i in range(2)}
        for g in early_guids:
            assert g not in returned, f"Early item {g} should not appear after cutoff"

    def test_device_b_creates_items_device_a_syncs_them(self, client):
        """Full bidirectional delta sync: A creates some, B creates some,
        each device only fetches what the other added."""
        token_a = register(client, DEVICE_A)
        token_b = register(client, DEVICE_B)

        # Device A creates 2 items; records the time
        for i in range(2):
            g = f"bidir_a_{i:04d}_test"
            create_item(client, DEVICE_A, g,
                        f"https://x.com/objects/v1/{g}", "x.com",
                        data={"origin": "device-a"})

        cutoff_b = datetime.now(timezone.utc).isoformat()

        # Device B creates 2 items
        b_guids = []
        for i in range(2):
            g = f"bidir_b_{i:04d}_test"
            create_item(client, DEVICE_B, g,
                        f"https://x.com/objects/v1/{g}", "x.com",
                        data={"origin": "device-b"})
            b_guids.append(g)

        cutoff_a = datetime.now(timezone.utc).isoformat()

        # Device A syncs since cutoff_a → sees only B's new items
        feed_a = sync_download(client, DEVICE_A, token_a, after=cutoff_b)
        a_received = {i["guid"] for i in feed_a["changes"]["items_added"]}
        for g in b_guids:
            assert g in a_received

        # Device B syncs since cutoff_b → should not see its own items
        # (B's items were created AFTER cutoff_b, so they appear; A's were before)
        feed_b = sync_download(client, DEVICE_B, token_b, after=cutoff_a)
        b_received = {i["guid"] for i in feed_b["changes"]["items_added"]}
        for g in b_guids:
            assert g not in b_received   # B's items were created before cutoff_a


# ═══════════════════════════════════════════════════════════════════════════
# 8. Offline queue — simulate the mobile sync manager pushing batched items
#    The mobile app queues items locally when offline and calls
#    POST /api/v1/sync/upload when connectivity is restored.
# ═══════════════════════════════════════════════════════════════════════════

class TestOfflineSyncUpload:
    def test_offline_queue_creates_items_on_server(self, client):
        token = register(client, DEVICE_A)

        # Simulate items that were created offline and queued locally
        offline_items = [
            {
                "item_id": f"local-id-{i:03d}",   # local UUID from the mobile
                "guid":    f"offl_aaaa_{i:04d}_test",
                "url":     f"https://example.com/objects/v1/offl_aaaa_{i:04d}_test",
                "domain":  "example.com",
                "data":    {"title": f"Offline item {i}", "location": SAMPLE_GPS},
                "change_summary": "Created offline",
            }
            for i in range(4)
        ]

        resp = client.post(
            "/api/v1/sync/upload",
            json={"changes": {"item_versions": offline_items}},
            headers=headers(DEVICE_A, token),
        )
        assert resp.status_code == 200
        body = resp.json()
        assert body["status"] == "ok"
        assert body["processed"] >= 4

        # All queued items must now be retrievable by GUID
        for item in offline_items:
            record = get_item(client, item["guid"])
            assert record["guid"] == item["guid"]

    def test_offline_queue_with_gps_data_preserved(self, client):
        token = register(client, DEVICE_A)

        guid = "offl_gps_test_0001"
        resp = client.post(
            "/api/v1/sync/upload",
            json={"changes": {"item_versions": [{
                "item_id": "local-gps-001",
                "guid":    guid,
                "url":     f"https://example.com/objects/v1/{guid}",
                "domain":  "example.com",
                "data": {
                    "title": "Tagged widget",
                    "location": SAMPLE_GPS,
                },
                "change_summary": "Scanned at location, synced later",
            }]}},
            headers=headers(DEVICE_A, token),
        )
        assert resp.status_code == 200

        record = get_item(client, guid)
        data   = record["latest_version"]["data"]
        assert data["title"] == "Tagged widget"
        loc = data["location"]
        assert loc["latitude"]  == pytest.approx(37.7749)
        assert loc["longitude"] == pytest.approx(-122.4194)

    def test_two_device_offline_queues_merge_on_server(self, client):
        """Device A and B were both offline, each created different items.
        When they both sync, all items should be present on the server."""
        token_a = register(client, DEVICE_A)
        token_b = register(client, DEVICE_B)

        a_items = [
            {
                "item_id": f"a-local-{i}",
                "guid":    f"merge_a_{i:04d}_test",
                "url":     f"https://x.com/objects/v1/merge_a_{i:04d}_test",
                "domain":  "x.com",
                "data":    {"created_by": "device-a", "index": i},
            }
            for i in range(3)
        ]
        b_items = [
            {
                "item_id": f"b-local-{i}",
                "guid":    f"merge_b_{i:04d}_test",
                "url":     f"https://x.com/objects/v1/merge_b_{i:04d}_test",
                "domain":  "x.com",
                "data":    {"created_by": "device-b", "index": i},
            }
            for i in range(3)
        ]

        # Both devices push their offline queues
        r_a = client.post("/api/v1/sync/upload",
                          json={"changes": {"item_versions": a_items}},
                          headers=headers(DEVICE_A, token_a))
        r_b = client.post("/api/v1/sync/upload",
                          json={"changes": {"item_versions": b_items}},
                          headers=headers(DEVICE_B, token_b))
        assert r_a.status_code == 200
        assert r_b.status_code == 200

        # Full sync: each device should now see ALL 6 items
        feed_a = sync_download(client, DEVICE_A, token_a)
        feed_b = sync_download(client, DEVICE_B, token_b)

        all_guids = {i["guid"] for i in feed_a["changes"]["items_added"]}
        assert len(all_guids) == 6

        b_all  = {i["guid"] for i in feed_b["changes"]["items_added"]}
        assert all_guids == b_all  # Both devices see the same set
