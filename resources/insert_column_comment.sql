INSERT INTO column_comment (
  instance_name,
  table_schema,
  table_name,
  column_name,
  column_comment
) SELECT
  *
FROM
  (
    SELECT
      ? AS instance_name,
      ? AS table_schema,
      ? AS table_name,
      ? AS column_name,
      ? AS column_comment
  ) r1
WHERE
  NOT EXISTS (
    SELECT
      1
    FROM
      column_comment r2
    WHERE
      (
        r1.instance_name = r2.instance_name
        OR r1.instance_name IS NULL AND r2.instance_name IS NULL
      )
      AND (
        r1.table_schema = r2.table_schema
        OR r1.table_schema IS NULL AND r2.table_schema IS NULL
      )
      AND (
        r1.table_name = r2.table_name
        OR r1.table_name IS NULL AND r2.table_name IS NULL
      )
      AND r1.column_name = r2.column_name)