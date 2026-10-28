-- Who closed a drift event and why. Closing a breach is a sign-off, so the note and the person are kept with it.
alter table drift_events add column resolution_note varchar(2048);
alter table drift_events add column resolved_by uuid;
alter table drift_events add column resolved_at timestamp with time zone;
