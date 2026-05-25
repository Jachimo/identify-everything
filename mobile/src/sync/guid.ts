const BASE26 = "abcdefghijklmnop0123456789";
const BLOCK_SIZE = 4;
const BLOCKS = 4;
const SEPARATOR = "_";

const GUID_PATTERN = /^[a-p0-9]{4}_[a-p0-9]{4}_[a-p0-9]{4}_[a-p0-9]{4}$/;
const GUID_PATTERN_NOSEPS = /^[a-p0-9]{16}$/;

function randomBase26Char(): string {
  return BASE26[Math.floor(Math.random() * BASE26.length)];
}

export function generateGuid(): string {
  const blocks: string[] = [];
  for (let b = 0; b < BLOCKS; b++) {
    let block = "";
    for (let i = 0; i < BLOCK_SIZE; i++) {
      block += randomBase26Char();
    }
    blocks.push(block);
  }
  return blocks.join(SEPARATOR);
}

export function isValidGuid(guid: string | null | undefined): boolean {
  if (!guid) return false;
  return GUID_PATTERN.test(guid) || GUID_PATTERN_NOSEPS.test(guid);
}

export function normalizeGuid(guid: string | null | undefined): string | null {
  if (!guid) return null;
  const stripped = guid.replace(/_/g, "");
  if (stripped.length !== 16) return null;
  return (
    stripped.substring(0, 4) + "_" +
    stripped.substring(4, 8) + "_" +
    stripped.substring(8, 12) + "_" +
    stripped.substring(12, 16)
  );
}

export function buildItemUrl(domain: string, guid: string): string {
  return `https://${domain}/objects/v1/${guid}`;
}