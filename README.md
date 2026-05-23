# The "Identify Everything" Project

## What Is This?

A system combining software and printable labels to identify items in
the real world, including keeping track of their location, ownership,
contents, and other properties.

## Project Goals

- Facilitate creation of printable stick-on labels with a unique QR
code, that can be scanned to produce a URL that will provide
information about the object.

- Use a URL schema that ensures every sticker (and thus item) has a
unique value, under a DNS domain and prefix chosen by the user, to
allow it to build on a DNS domain they own, or the Handle System, or
another managed URL namespace. 

- Allow a user with a smartphone to use a custom app and quickly scan
a printed QR label, and enter information and attach files which
become part of the digital record associated with that label's unique
value, stored in a searchable, self-hosted database.

- Maintain a versioned history of changes to each label/item record
when changes are made over time, never actually deleting information,
but keeping a timestamped history of changes, with the newest version
used as the canonical one

- Allow the item records database to be easily sharded and reconciled
between devices, to support "offline" scenarios where labels are
applied to items, scanned using a smartphone, and data entered for the
item record, all offline. The records are then uploaded to the online
database later, where they are combined with existing data.

- Allow subsets of data to be extracted from the item records database
and copied to an offline device, where records might be updated, and
then the database re-synched with the online database. 

- Allow different metadata schemas to be used for different types of
objects. Example: An item of type 'book' might have very different
properties in its item record (author, title, isbn) than an item of
type 'clothing' (size, subtype, style).  

- Any required fields or validation rules in a schema are enforced by
client (scanning and data entry) applications only, not by the
database or API layers.

- Be as simple and obvious as possible in its architecture and
implementation, to faciliate future users who may have lost all
documentation to understand how to use it, and to facilitate use cases
that we cannot anticipate at this time. 

- Build on existing technologies and organizational systems where
possible and consistent with other goals. Example: Items that normally
have serial numbers should include this serial number in the item
record, and potentially in the URL embedded in the printable label
(requires labels to be printed specifically for the serialized item,
not just pre-printed as a batch of unique arbitrary URLs).

- Avoid "reinventing the wheel" and solving problems that have already
been solved in the open source software ecosystem.

- Use common programming languages and technologies to ease future
maintenance and allow operation into the future with minimal
maintenance.


## Components

The system has several components:

- Documentation of the format used to create the printable labels,
which will be some type of 1D or 2D printable bar code.

- Documentation of the schema used in the URLs embedded in the
printable labels.

- A label generator, which creates printable QR-code labels either
singly or as a batch, suitably formatted for common printable labels
(Avery-type office labels) or label printers.  The label generator
must print randomly-generated identifier labels, which can be printed
in advance and used to identify anything, and *also* custom-URL labels
which incorporate specific elements into the URL from an
existing/external identification system (serial numbers).

- A central database, probably of the "document" database type, in
order to store the item records and allow searching of all item
records.

- A mobile app for Android smartphones, which allows item labels to be
scanned, the URL decoded, and then allows the user to enter
information about the item, which will become part of its item record.
Information may include structured key/value data (metadata) or
unstructured data as attachments (text, photos, video, etc.).  The
mobile app will automatically add the GPS location and date/time of
the update to the record, to facilitate merging records for the same
item identifier (URL).  The app will upload new or edited item records
when a connection to the central database is possible, and queue them
for later delivery if it is not.

- FUTURE STATE: A similar app for the iOS platform.

- FUTURE STATE: A web interface to the 'central database' which allows
searching based on structured or unstructured data, location and time,
and attachment data.
