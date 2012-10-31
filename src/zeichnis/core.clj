(ns zeichnis.core)

(def default-peer (atom nil))

(defprotocol IZeichnisPeer
  "A ZeichnisPeer manages connections to a set of storage layers and logical databases, allowing to distribute operations and overlay results."
  (get-db [this db-id]))

(defprotocol IDatabase
  "A database recieves requests, runs these as actions and translates calls from these actions to db-op into calls to the storage backend."
  (db-action [this action-id]))

(defprotocol IGetDatastore
  (get-ds [this]))

(defn z
  ([args] (z @default-peer args))
  ([peer args]
     (let [db (get-db peer (:db args))]
       (db-action db args))))

(deftype ZeichnisPeer [databases]
  IZeichnisPeer
  (get-db [this db-id]
    (databases db-id)))

(defn fmap [f m]
  "Update all keys in map"
  (into (empty m) (for [[k v] m] [k (f v)])))

;; these are init multimethods for datastore and database config maps
(defmulti init-ds :type)
(defmulti init-db #(:type (first %)))

(defn init-peer [database-conf datastore-conf]
  (let [dss (fmap init-ds datastore-conf)
        dbs (fmap #(init-db % dss) database-conf)]
    (ZeichnisPeer. dbs)))

(defn init-default-peer
  ([db-conf ds-conf]
     (init-default-peer db-conf ds-conf false))
  ([db-conf ds-conf hard?]
     (if (or hard? (nil? @default-peer)) ; this is not entirely safe, but it's just meant to catch the stupid anyway
       (reset! default-peer (init-peer db-conf ds-conf))
       (throw (Exception. "Default peer should not be initialized more than once, try update-peer")))))

