SELECT
	r1.instance_name,
	r1.table_schema,
	r1.table_name,
	r1.table_logical_name,
	r1.table_comment,
	r2.column_name,
	r2.column_logical_name,
	r2.column_comment
FROM
	tables r1
INNER JOIN
	columns r2
USING (instance_name, table_schema, table_name)
WHERE
  r1.instance_name LIKE ?
  AND r1.table_schema LIKE ?
	AND (
		r1.table_name LIKE ?
		OR r1.table_comment LIKE ?
	)
	AND (
		r2.column_name LIKE ?
		OR r2.column_comment LIKE ?
	)