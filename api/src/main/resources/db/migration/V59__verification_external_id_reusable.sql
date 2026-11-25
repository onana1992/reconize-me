DO $$
DECLARE
    constraint_name text;
    index_name text;
BEGIN
    FOR constraint_name IN
        SELECT c.conname
        FROM pg_constraint c
        JOIN pg_class t ON t.oid = c.conrelid
        WHERE t.relname = 'verifications'
          AND c.contype = 'u'
          AND (
              SELECT string_agg(a.attname, ',' ORDER BY a.attname)
              FROM unnest(c.conkey) k(attnum)
              JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = k.attnum
          ) = 'external_id,organization_id'
    LOOP
        EXECUTE format('ALTER TABLE verifications DROP CONSTRAINT %I', constraint_name);
    END LOOP;

    FOR index_name IN
        SELECT i.relname
        FROM pg_index ix
        JOIN pg_class i ON i.oid = ix.indexrelid
        JOIN pg_class t ON t.oid = ix.indrelid
        WHERE t.relname = 'verifications'
          AND ix.indisunique
          AND (
              SELECT string_agg(a.attname, ',' ORDER BY a.attname)
              FROM unnest(ix.indkey) k(attnum)
              JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = k.attnum
          ) = 'external_id,organization_id'
    LOOP
        EXECUTE format('DROP INDEX IF EXISTS %I', index_name);
    END LOOP;
END $$;
