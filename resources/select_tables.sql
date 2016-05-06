SELECT
	instance_name,
	table_schema,
	table_name,
	table_logical_name,
	table_comment
FROM
	tables
WHERE
  instance_name LIKE ?
  AND table_schema LIKE ?
  AND (
    table_name LIKE ?
    OR table_comment LIKE ?
  )
