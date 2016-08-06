(ns desc-bot.core-test
  (:require [clojure.test :refer :all]
            [desc-bot.core :refer :all]
            [clojure.java.jdbc :as jdbc]))

; Util
(defn equalAsSet [xs ys]
  (= (set xs) (set ys)))

; Set up
(defn empty-db []
  (jdbc/execute! mysql-db "TRUNCATE tables_org")
  (jdbc/execute! mysql-db "TRUNCATE columns_org")
  (jdbc/execute! mysql-db "TRUNCATE table_comment")
  (jdbc/execute! mysql-db "TRUNCATE column_comment"))

(defn fill-db []
  (jdbc/execute! mysql-db "INSERT INTO tables_org (instance_name, table_schema, table_name) VALUES
                             ('com', 's01', 't01'),
                             ('com', 's01', 't02'),
                             ('com', 's01', 't03'),
                             ('com', 's02', 't01'),
                             ('com', 's02', 't03'),
                             ('com', 's02', 't04'),
                             ('com', 's02', 't05'),
                             ('com', 's03', 't06')
                             ")
  (jdbc/execute! mysql-db "INSERT INTO table_comment (instance_name, table_schema, table_name, table_comment) VALUES
                             ('com', null, 't01', 't_cmmnt01')")
  (jdbc/execute! mysql-db "INSERT INTO columns_org (instance_name, table_schema, table_name, column_name) VALUES
                             ('com', 's01', 't02', 'c01'),
                             ('com', 's01', 't03', 'c02'),
                             ('com', 's02', 't03', 'c02'),
                             ('com', 's01', 't03', 'c03'),
                             ('com', 's02', 't04', 'c03'),
                             ('com', 's02', 't05', 'c04'),
                             ('com', 's03', 't06', 'c04')
                             ")
  (jdbc/execute! mysql-db "INSERT INTO column_comment (instance_name, table_schema, table_name, column_name, column_comment) VALUES
                             ('com', null, 't03', 'c02', 'c_cmmnt01'),
                             ('com', null, null, 'c03', 'c_cmmnt02')
                             ")
  )

(defn with-data [f]
  (empty-db)
  (fill-db)
  (f)
  ;(empty-db)
  )

(use-fixtures :each with-data)

; Test
(deftest ut-replace-wildcard
  (is (= "" (replace-wildcard "")))
  (is (= "%" (replace-wildcard "*")))
  (is (= "a" (replace-wildcard "a")))
  (is (= "a%" (replace-wildcard "a*")))
  (is (= "%a" (replace-wildcard "*a")))
  (is (= "a%b" (replace-wildcard "a*b")))
  )

(deftest ut-select
  (is (equalAsSet (select Tables {:instance_name "com" :table_schema "s01" :table_name "t01"})
                  [{:instance_name "com" :table_schema "s01" :table_name "t01" :table_logical_name nil :table_comment "t_cmmnt01"}]))
  (is (equalAsSet (select Tables {:instance_name "com" :table_schema "s02" :table_name "t01"})
                  [{:instance_name "com" :table_schema "s02" :table_name "t01" :table_logical_name nil :table_comment "t_cmmnt01"}]))
  (is (equalAsSet (select Columns {:instance_name "com" :table_schema "s01" :table_name "t02"})
                  [{:instance_name "com" :table_schema "s01" :table_name "t02" :column_name "c01" :column_logical_name nil :column_comment nil}]))
  (is (equalAsSet (select Columns {:instance_name "com" :table_schema "s01" :table_name "t03"})
                  [{:instance_name "com" :table_schema "s01" :table_name "t03" :column_name "c02" :column_logical_name nil :column_comment "c_cmmnt01"}
                   {:instance_name "com" :table_schema "s01" :table_name "t03" :column_name "c03" :column_logical_name nil :column_comment "c_cmmnt02"}]))
  (is (equalAsSet (select Columns {:instance_name "com" :table_schema "s02" :table_name "t03"})
                  [{:instance_name "com" :table_schema "s02" :table_name "t03" :column_name "c02" :column_logical_name nil :column_comment "c_cmmnt01"}]))
  (is (equalAsSet (select Columns {:instance_name "com" :table_schema "s02" :table_name "t04"})
                  [{:instance_name "com" :table_schema "s02" :table_name "t04" :column_name "c03" :column_logical_name nil :column_comment "c_cmmnt02"}]))
  (is (equalAsSet (select Columns {:instance_name "com" :table_schema "s01" :table_name "t02" :column_name "c01"})
                  [{:instance_name "com" :table_schema "s01" :table_name "t02" :column_name "c01" :column_logical_name nil :column_comment nil}]))
  (is (equalAsSet (select Tables {:instance_name "com" :table_schema "s01" :table_name "z01"})
                  []))
  (is (equalAsSet (select Tables {:instance_name "com" :table_schema "s01" :table_name ""})
                  []))
  (is (equalAsSet (select Tables {:instance_name "com" :table_schema "z01" :table_name "t01"})
                  []))
  (is (equalAsSet (select Tables {:instance_name "com" :table_schema "" :table_name "t01"})
                  []))
  (is (thrown? IllegalArgumentException (select)))
  (is (thrown? IllegalArgumentException (select Tables)))
  (is (thrown? IllegalArgumentException (select Columns)))
  (is (thrown? IllegalArgumentException (select Tables {})))
  (is (thrown? IllegalArgumentException (select Columns {})))
  (is (thrown? IllegalArgumentException (select Tables {:instance_name "com" :table_schema "s01"})))
  (is (thrown? IllegalArgumentException (select Columns {:instance_name "com" :table_schema "s01"})))
  (is (thrown? IllegalArgumentException (select Tables {:instance_name "com" :table_name "t01"})))
  (is (thrown? IllegalArgumentException (select Columns {:instance_name "com" :table_name "t01"})))
  (is (thrown? IllegalArgumentException (select Columns {:instance_name "com" :table_schema "s01" :table_name "t01" :column_comment "z"})))
  )

(deftest ut-select-like
  (is (= (select-like ["t01"])
         [{:instance_name "com" :table_schema "s01" :table_name "t01" :table_logical_name nil :table_comment "t_cmmnt01"}
          {:instance_name "com" :table_schema "s02" :table_name "t01" :table_logical_name nil :table_comment "t_cmmnt01"}]))
  (is (= (select-like ["t01*"])
         [{:instance_name "com" :table_schema "s01" :table_name "t01" :table_logical_name nil :table_comment "t_cmmnt01"}
          {:instance_name "com" :table_schema "s02" :table_name "t01" :table_logical_name nil :table_comment "t_cmmnt01"}]))
  (is (= (select-like ["*t01"])
         [{:instance_name "com" :table_schema "s01" :table_name "t01" :table_logical_name nil :table_comment "t_cmmnt01"}
          {:instance_name "com" :table_schema "s02" :table_name "t01" :table_logical_name nil :table_comment "t_cmmnt01"}]))
  (is (= (select-like ["t*1"])
         [{:instance_name "com" :table_schema "s01" :table_name "t01" :table_logical_name nil :table_comment "t_cmmnt01"}
          {:instance_name "com" :table_schema "s02" :table_name "t01" :table_logical_name nil :table_comment "t_cmmnt01"}]))
  (is (= (select-like ["t02" "c01"])
         [{:instance_name "com" :table_schema "s01" :table_name "t02" :table_logical_name nil :table_comment nil :column_name "c01" :column_logical_name nil :column_comment nil}]))
  (is (= (select-like ["t02*" "c01"])
         [{:instance_name "com" :table_schema "s01" :table_name "t02" :table_logical_name nil :table_comment nil :column_name "c01" :column_logical_name nil :column_comment nil}]))
  (is (= (select-like ["*t02" "c01"])
         [{:instance_name "com" :table_schema "s01" :table_name "t02" :table_logical_name nil :table_comment nil :column_name "c01" :column_logical_name nil :column_comment nil}]))
  (is (= (select-like ["t*2" "c01"])
         [{:instance_name "com" :table_schema "s01" :table_name "t02" :table_logical_name nil :table_comment nil :column_name "c01" :column_logical_name nil :column_comment nil}]))
  (is (= (select-like ["t02" "c01*"])
         [{:instance_name "com" :table_schema "s01" :table_name "t02" :table_logical_name nil :table_comment nil :column_name "c01" :column_logical_name nil :column_comment nil}]))
  (is (= (select-like ["t02" "*c01"])
         [{:instance_name "com" :table_schema "s01" :table_name "t02" :table_logical_name nil :table_comment nil :column_name "c01" :column_logical_name nil :column_comment nil}]))
  (is (= (select-like ["t02" "c*1"])
         [{:instance_name "com" :table_schema "s01" :table_name "t02" :table_logical_name nil :table_comment nil :column_name "c01" :column_logical_name nil :column_comment nil}]))
  (is (= (select-like ["z01"]) []))
  (is (= (select-like ["z*"]) []))
  (is (thrown? IllegalArgumentException (select-like)))
  (is (thrown? IllegalArgumentException (select-like [])))
  (is (thrown? IllegalArgumentException (select-like ["t02" "c01" "x01"])))
  )

(deftest ut-insert-or-update
  (is (= (insert-or-update "foo" ["com" "s01" "t01"])
         [{:instance_name "com" :table_schema "s01" :table_name "t01" :table_logical_name nil :table_comment "foo"}]))
  (is (= (insert-or-update "bar" ["com" "s02" "t01"])
         [{:instance_name "com" :table_schema "s02" :table_name "t01" :table_logical_name nil :table_comment "bar"}]))
  (is (= (insert-or-update "" ["com" "s01" "t01"])
         [{:instance_name "com" :table_schema "s01" :table_name "t01" :table_logical_name nil :table_comment "t_cmmnt01"}]))
  (is (= (insert-or-update "" ["com" "s02" "t01"])
         [{:instance_name "com" :table_schema "s02" :table_name "t01" :table_logical_name nil :table_comment "t_cmmnt01"}]))
  (is (= (insert-or-update "baz" ["com" "*" "t01"])
         [{:instance_name "com" :table_schema "s01" :table_name "t01" :table_logical_name nil :table_comment "baz"}
          {:instance_name "com" :table_schema "s02" :table_name "t01" :table_logical_name nil :table_comment "baz"}]))
  (is (= (insert-or-update "" ["com" "z01" "t01"])
         []))
  (is (= (insert-or-update "" ["com" "s01" "z01"])
         []))
  (is (= (insert-or-update "foo" ["com" "s01" "t02" "c01"])
         [{:instance_name "com" :table_schema "s01" :table_name "t02" :column_name "c01" :column_logical_name nil :column_comment "foo"}]))
  (is (= (insert-or-update "" ["com" "s01" "t02" "c01"])
         [{:instance_name "com" :table_schema "s01" :table_name "t02" :column_name "c01" :column_logical_name nil :column_comment nil}]))
  (is (= (insert-or-update "bar" ["com" "*" "t05" "c04"])
         [{:instance_name "com" :table_schema "s02" :table_name "t05" :column_name "c04" :column_logical_name nil :column_comment "bar"}]))
  (is (= (insert-or-update "baz" ["com" "*" "*" "c04"])
         [{:instance_name "com" :table_schema "s02" :table_name "t05" :column_name "c04" :column_logical_name nil :column_comment "bar"}
          {:instance_name "com" :table_schema "s03" :table_name "t06" :column_name "c04" :column_logical_name nil :column_comment "baz"}]))
  (is (= (insert-or-update "" ["com" "z01" "t01" "c01"])
         []))
  (is (= (insert-or-update "" ["com" "s01" "z01" "c01"])
         []))
  (is (= (insert-or-update "" ["com" "s01" "t01" "z01"])
         []))
  (is (thrown? IllegalArgumentException (insert-or-update)))
  (is (thrown? IllegalArgumentException (insert-or-update "")))
  (is (thrown? IllegalArgumentException (insert-or-update "" [])))
  (is (thrown? IllegalArgumentException (insert-or-update "" ["com" "s01"])))
  ;(is (thrown? IllegalArgumentException (insert-or-update "" ["com" "s01" "t02" "c01" "x01"])))
  )

(deftest ut-parse-set-pattern
  (is (= ["b" ["a"]] (parse-set-pattern "a='b'")))
  (is (= ["=b" ["a"]] (parse-set-pattern "a='=b'")))
  (is (= ["==b" ["a"]] (parse-set-pattern "a='==b'")))
  (is (= ["b=" ["a"]] (parse-set-pattern "a='b='")))
  (is (= ["b=c" ["a"]] (parse-set-pattern "a='b=c'")))
  (is (= ["b=c=d" ["a"]] (parse-set-pattern "a='b=c=d'")))
  (is (= ["b" ["a"]] (parse-set-pattern "a = 'b'")))
  (is (= ["b" ["a"]] (parse-set-pattern "a=b")))
  (is (= ["" ["a"]] (parse-set-pattern "a=''")))
  (is (= ["c" ["a" "b"]] (parse-set-pattern "a.b='c'")))
  (is (= ["c" ["a" "b"]] (parse-set-pattern " a.b='c'")))
  (is (= ["c" ["a" "b"]] (parse-set-pattern "a .b='c'")))
  (is (= ["c" ["a" "b"]] (parse-set-pattern "a. b='c'")))
  (is (= ["c" ["a" "b"]] (parse-set-pattern "a.b ='c'")))
  (is (= ["d" ["a" "b" "c"]] (parse-set-pattern "a.b.c='d'")))
  (is (thrown? IllegalStateException (parse-set-pattern "")))
  (is (thrown? IllegalStateException (parse-set-pattern "a")))
  (is (thrown? IllegalStateException (parse-set-pattern "='b'")))
  (is (thrown? IllegalStateException (parse-set-pattern "a=")))
  )

(deftest ut-command-route
  (binding [search-comment (fn [table-pattern] table-pattern)
            set-comment (fn [set-pattern] set-pattern)]
    (is (= ["x" "y"] (command-route "show" "x y")))
    (is (= [""] (command-route "show" "")))
    (is (thrown? IllegalStateException (command-route "z" "x y")))
    (is (thrown? IllegalArgumentException (command-route)))
    (is (thrown? IllegalArgumentException (command-route "x")))
    (is (thrown? IllegalArgumentException (command-route "x" "y" "z")))
    )
  )

(deftest ut-apply-command
  (binding [command-route (fn [command option] {:command command :option option})]
    (is (= {:command "x" :option "y"} (apply-command "x y")))
    (is (= {:command "x" :option "y"} (apply-command " x y")))
    (is (= {:command "x" :option "y"} (apply-command "x  y")))
    (is (= {:command "x" :option "y z"} (apply-command "x  y z")))
    (is (= {:command "x" :option "y z"} (apply-command "x y z ")))
    )
  )