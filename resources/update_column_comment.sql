UPDATE column_comment SET column_comment = ?
WHERE
  CASE WHEN instance_name IS NULL THEN ? ELSE instance_name END = ?
	AND CASE WHEN table_schema IS NULL THEN ? ELSE table_schema END = ?
	AND CASE WHEN table_name IS NULL THEN ? ELSE table_name END = ?
	AND column_name = ?