(ns desc-bot.core
  (:require
    [clojure.core.match :refer [match]]
    [clojure.java.jdbc :as jdbc]
    [clojure.string :as str]
    [clojure.java.io :as io]))

; Constants
(def Tables "tables")
(def Columns "columns")
(def NullValue "|NULLVALUE|")

;; Common DB
(def mysql-db {:subprotocol "mysql"
               :subname     "//localhost:3307/ub_information_schema"
               :user        "system"
               :password    "system"})

(defn replace-wildcard [s]
  (str/replace s "*" "%"))

(defn complete-null-literal [null-literal value]
  (if (or (nil? value) (= "*" value)) null-literal value))

(defn construct-restriction-vals [pattern]
  (map #(replace-wildcard %) (reverse (str/split pattern #"\."))))

; DML
(defmulti select (fn [_ restrict-columns] (set (keys restrict-columns))))
(defmulti select-like (fn [patterns] (count patterns)))
(defmulti insert-or-update (fn [_ keys] (count keys)))

(defmethod select :default [_ _]
  (throw (IllegalArgumentException.)))

(defmethod select-like :default [_]
  (throw (IllegalArgumentException.)))

(defmethod insert-or-update :default [_ _]
  (throw (IllegalArgumentException.)))

(defmethod select #{:instance_name :table_schema :table_name} [view-name restriction-vals]
  (let [{instance-name :instance_name
         table-schema  :table_schema
         table-name    :table_name} restriction-vals]
    (jdbc/query mysql-db [
                          (str "SELECT * FROM " view-name " WHERE instance_name LIKE ? AND table_schema LIKE ? AND table_name = ?")
                          (replace-wildcard instance-name)
                          (replace-wildcard table-schema)
                          table-name]
                {:row-fn identity})))

;; tables
(defmethod select-like 1 [restriction-strs]
  (let [[table-name table-schema instance-name] (construct-restriction-vals (first restriction-strs))
        result (jdbc/query mysql-db [
                                     (slurp (io/resource "select_tables.sql"))
                                     (complete-null-literal "%" instance-name)
                                     (complete-null-literal "%" table-schema)
                                     table-name
                                     table-name]
                           {:row-fn identity})]
    (if (= 1 (count result))
      (let [{instance-name :instance_name
             table-schema  :table_schema
             table-name    :table_name} (first result)]
        (concat result (select Columns {:instance_name instance-name :table_schema table-schema :table_name table-name})))
      result)))

(defmethod insert-or-update 3 [comment restriction-vals]
  (let [[instance-name table-schema table-name] restriction-vals
        comment (if (empty? comment) nil comment)]
    (jdbc/execute! mysql-db [
                             (slurp (io/resource "update_table_comment.sql"))
                             comment
                             NullValue
                             (complete-null-literal NullValue instance-name)
                             NullValue
                             (complete-null-literal NullValue table-schema)
                             table-name])
    (jdbc/execute! mysql-db [
                             (slurp (io/resource "insert_table_comment.sql"))
                             (complete-null-literal nil instance-name)
                             (complete-null-literal nil table-schema)
                             table-name
                             comment])
    (select Tables {:instance_name instance-name :table_schema table-schema :table_name table-name})))

;; columns
(defmethod select #{:instance_name :table_schema :table_name :column_name} [view-name restriction-vals]
  (let [{instance-name :instance_name
         table-schema  :table_schema
         table-name    :table_name
         column-name   :column_name} restriction-vals]
    (jdbc/query mysql-db [
                          (str "SELECT * FROM " view-name " WHERE instance_name LIKE ? AND table_schema LIKE ? AND table_name LIKE ? AND column_name = ?")
                          (replace-wildcard instance-name)
                          (replace-wildcard table-schema)
                          (replace-wildcard table-name)
                          column-name]
                {:row-fn identity})))

(defmethod select-like 2 [restriction-strs]
  (let [[table-name table-schema instance-name] (construct-restriction-vals (first restriction-strs))
        column (replace-wildcard (second restriction-strs))]
    (jdbc/query mysql-db [
                          (slurp (io/resource "select_columns.sql"))
                          (complete-null-literal "%" instance-name)
                          (complete-null-literal "%" table-schema)
                          table-name
                          table-name
                          column
                          column]
                {:row-fn identity})))

(defmethod insert-or-update 4 [comment restriction-vals]
  (let [[instance-name table-schema table-name column-name] restriction-vals
        comment (if (empty? comment) nil comment)]
    (jdbc/execute! mysql-db [
                             (slurp (io/resource "update_column_comment.sql"))
                             comment
                             NullValue
                             (complete-null-literal NullValue instance-name)
                             NullValue
                             (complete-null-literal NullValue table-schema)
                             NullValue
                             (complete-null-literal NullValue table-name)
                             column-name])
    (jdbc/execute! mysql-db [
                             (slurp (io/resource "insert_column_comment.sql"))
                             (complete-null-literal nil instance-name)
                             (complete-null-literal nil table-schema)
                             (complete-null-literal nil table-name)
                             column-name
                             comment])
    (select Columns {:instance_name instance-name :table_schema table-schema :table_name table-name :column_name column-name})))

; Parse
(defn parse-set-pattern [set-pattern]
  (let [[restriction-str comment] (str/split set-pattern #"\s*=\s*")
        restriction-vals (str/split restriction-str #"\.")]
    (if (or (empty? comment) (empty? (first restriction-vals))) (throw (IllegalStateException.)))
    [(str/replace comment #"['\"]" "") restriction-vals]))

; Command
(defn ^:dynamic search-comment [restriction-strs]
  (select-like restriction-strs))

(defn ^:dynamic set-comment [set-pattern]
  (apply insert-or-update (parse-set-pattern set-pattern)))

; Routing
(defn ^:dynamic command-route [command option]
  (match [command]
         ["show"] (search-comment (str/split option #"\s+"))
         ["set"] (set-comment option)
         :else (throw (IllegalStateException.))))

(defn apply-command [command-text]
  (let [command (re-find #"\s*\w+\s+" command-text)
        option (subs command-text (count command))]
    (command-route (str/trim command) (str/trim option))))

; Main
(defn command-handler [command-text]
  (try
    (str/join "\n"
              (for [result (apply-command command-text)]
                (str "set "
                     (str/replace (str/join "." [(:instance_name result) (:table_schema result) (:table_name result) (:column_name result)]) #"\.$" "")
                     " = "
                     "'"
                     (:table_comment result)
                     (:column_comment result)
                     "'")))
    (catch Exception _ "That command pattern is not supported.")))
