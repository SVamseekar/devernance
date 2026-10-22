-- Keys issued before scopes existed keep acting with their creator's full role; new keys default to the
-- release gate only (ApiKeyService), so a leaked CI key cannot change systems, approvals or evidence.
alter table api_keys add column scope varchar(32) default 'FULL' not null;
