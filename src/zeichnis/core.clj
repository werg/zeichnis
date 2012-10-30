(ns zeichnis.core
  (:require [clojure.set :as cset]))

(def default-peer (atom nil))

(defn z
  ([args] (z @default-peer args))
  ([peer args]
     (let [db (get-db peer (:db args))]
       (db-action db args))))

(defprotocol IZeichnisPeer
  "A ZeichnisPeer manages connections to a set of storage layers and logical databases, allowing to distribute operations and overlay results."
  (get-db [this db-id]))

(defprotocol IDatabase
  "A database recieves requests, runs these as actions and translates calls from these actions to db-op into calls to the storage backend."
  (db-action [this action-id])
  (db-op [this args]))

(deftype ZeichnisPeer [databases]
  IZeichnisPeer
  (peer-z [this args]
    ;; potentially here we could filter for some special non-db functions
    (db-z (databases (:db args)) args))
  (get-db [this db-id]
    (databases db-id)))

(defn fmap [f m]
  "Update all keys in map"
  (into (empty m) (for [[k v] m] [k (f v)])))

;; these are init multimethods for datastore and database config maps
(defmulti init-ds :type)
(defmulti init-db #(:type (first %)))

;; do i by default want to pass in the different actions?
;; the action set should be extensible
;; databases can redefine actions
;; datastores can as well
;; so in many cases we probably just re-distribute the same operations to
;; all datastores

(defmethod init-db :default [conf dss]
  ((:type conf) (dss (:datastore (:conf conf)))))
(defmethod init-ds :default [conf]
  ((:type conf) conf))

(defn init-peer [database-conf datastore-conf]
  (let [dss (fmap init-ds datastore-conf)
        dbs (fmap #(init-db % dss) database-conf)]
    (ZeichnisPeer dbs)))

(defn init-default-peer
  ([db-conf ds-conf]
     (init-default-peer db-conf ds-conf false))
  ([db-conf ds-conf hard?]
     (if (or hard? (nil? @default-peer)) ; this is not entirely safe, but it's just meant to catch the stupid anyway
       (reset! default-peer (init-peer db-conf ds-conf))
       (throw (Exception. "Default peer should not be initialized more than once, try update-peer")))))


;; actions are higher level database tasks, ops are lower-level datastore directives
;; we externalize them as below (will still need to figure out whehter this is the right way to do it)
;; in order to enable extensibility of the instruction set
(def blobstore-actions (atom {}))
(def blobstore-ops (atom {}))

(deftype SingleBlobStoreDB [datastore]
  (get-action [this action-id]
    (@blobstore-actions action-id))
  (db-action [this args]
    ((get-action (:function args) (:input args))))
  (db-op [this args]
    ((@blobstore-ops (:function args)) (:input args))))

;; signature for database actions is (action db args)

(defmacro act [db k args]
  `(db-action ~db {:function ~k :input ~args}))

(defn insert-node [db args]
  "insert a node specified in the format {:bucket _ :content _} into the bucket. does not store!"
  (let [{:keys [bucket content]} args]
    (act db :insert-from {:target args
                          :from (act db :get-root {:bucket bucket})})))

(defn insert-from [db args]
  {:keys [from target]}
  (let [{:keys [children orphans]} (act :descend-towards args)]
    
    (doseq [child children]
      (act :insert-from {:from child :target target}))
    (doseq [orphan orphans]
      )))

;; yadda yadda