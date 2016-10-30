(ns desc-bot.core
  (:require
    [clojure.core.match :refer [match]]
    [clojure.java.jdbc :as jdbc]
    [clojure.string :as str]
    [compojure.core :refer :all]
    [compojure.route :as route]
    [compojure.handler :as handler]
    [ring.util.response :refer [response]]
    [ring.middleware.json :as json]
    [clojure.java.io :as io]))

; Constants
(def Tables "tables")
(def Columns "columns")
(def NullValue "|NULLVALUE|")

;; Common DB
; development
(def mysql-db {:subprotocol "mysql"
               :subname     "//localhost:3307/ub_information_schema?useUnicode=true&characterEncoding=utf8"
               :user        "system"
               :password    "system"})
; production
;(def mysql-db {:subprotocol "mysql"
;               :subname     "//localhost:3306/ub_information_schema?useUnicode=true&characterEncoding=utf8"
;               :user        "dbadmin"
;               :password    "administrator"})

(defn replace-wildcard [s]
  (str/replace s "*" "%"))

(defn complete-null-literal [null-literal value]
  (if (or (nil? value) (= "*" value)) null-literal value))

(defn construct-restriction-vals [pattern]
  (map #(replace-wildcard %) (reverse (str/split pattern #"\."))))

; DML
(defmulti select (fn [_ restrict-columns] (set (keys restrict-columns))))
(defmulti select-like (fn [patterns] (count patterns)))
(defmulti insert-or-update (fn [_ keys _] (count keys)))

(defmethod select :default [_ _]
  (throw (IllegalArgumentException.)))

(defmethod select-like :default [_]
  (throw (IllegalArgumentException.)))

(defmethod insert-or-update :default [_ _ _]
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

(defmethod insert-or-update 3 [comment restriction-vals user-name]
  (let [[instance-name table-schema table-name] restriction-vals
        comment (if (empty? comment) nil comment)]
    (jdbc/execute! mysql-db [
                             (slurp (io/resource "update_table_comment.sql"))
                             comment
                             user-name
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
                             comment
                             user-name])
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

(defmethod insert-or-update 4 [comment restriction-vals user-name]
  (let [[instance-name table-schema table-name column-name] restriction-vals
        comment (if (empty? comment) nil comment)]
    (jdbc/execute! mysql-db [
                             (slurp (io/resource "update_column_comment.sql"))
                             comment
                             user-name
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
                             comment
                             user-name])
    (select Columns {:instance_name instance-name :table_schema table-schema :table_name table-name :column_name column-name})))

; Parse
(defn parse-set-pattern [set-pattern]
  (let [[restriction-str comment] (str/split set-pattern #"\s*=\s*" 2)
        restriction-vals (map str/trim (str/split restriction-str #"\."))]
    (if (or (empty? comment) (empty? (first restriction-vals))) (throw (IllegalStateException.)))
    [(str/replace comment #"['\"]" "") restriction-vals]))

; Command
(defn ^:dynamic search-comment [restriction-strs]
  (if (not (re-find #"[^\*]" (apply str restriction-strs)))
    (throw (IllegalStateException. "Wildcard Only Search Not Allowed"))
    (select-like restriction-strs)))

(defn ^:dynamic set-comment [set-pattern user-name]
  (apply insert-or-update (conj (parse-set-pattern set-pattern) user-name)))

(defn enclose-single-backticks [str]
  (str "`" str "`"))

(defn enclose-triple-backticks [str]
  (str "```" str "```"))

(defn get-help-txt []
  (slurp (io/resource "help.txt")))

; Routing
(defn ^:dynamic command-route [command option user-name]
  (match [command]
         ["show"] (search-comment (str/split option #"\s+"))
         ["set"] (set-comment option user-name)
         ["help"] (throw (IllegalStateException. (get-help-txt)))
         :else (throw (IllegalStateException.))))

(defn apply-command [command-text user-name]
  (let [command (or (re-find #"\s*\w+\s+" command-text) (re-find #"\s*help\s*" command-text))
        option (subs command-text (count command))]
    (command-route (str/trim command) (str/trim option) user-name)))

(defn struct-set-command-text [result]
  (str "set "
       (str/replace (str/join "." [(:instance_name result) (:table_schema result) (:table_name result) (:column_name result)]) #"\.$" "")
       " = "
       "\""
       (when (not (and (contains? result :table_name) (contains? result :column_name))) (:table_comment result))
       (:column_comment result)
       "\""))

(defn execute-command [all-command-text user-name]
  (for [command-text (str/split-lines all-command-text)] (apply-command command-text user-name)))

(defn struct-result-str [all-command-text user-name]
  (str
    (enclose-single-backticks all-command-text)
    "\n"
    (enclose-triple-backticks
      (let [result-all (for [result (apply concat (execute-command all-command-text user-name))] (struct-set-command-text result))]
        (if (zero? (count result-all))
          "No results"
          (str/join "\n" result-all))))))

; Main
(defn command-handler [all-command-text user-name]
  (try
    (struct-result-str all-command-text user-name)
    (catch Exception e
      (or (.getMessage e)
          (str "Command Not Supported\n" (get-help-txt))))))

;; Web Routing
(defroutes app-routes
           (POST "/" [token team_domain channel_name text user_name]
             (when (not (= user_name "slackbot"))
               (if (and (= token "qcMyEI7XOeDcFa3woJrOXQvP")
                        (= team_domain "uzabase")
                        (= channel_name "sp-db-desc"))
                 (response {:text (command-handler text user_name)})
                 (response {:text "Authentication Failed"}))))
           (route/not-found "Not Found"))

(def app
  (-> (handler/api app-routes)
      (json/wrap-json-body)
      (json/wrap-json-response)))
