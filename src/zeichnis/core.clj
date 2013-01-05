(ns zeichnis.core)

(def default-peer
  "The peer that the z function refers to if not supplied otherwise"
  (atom nil))

(defprotocol IZeichnisPeer
  "A ZeichnisPeer manages connections to a set of storage layers and logical databases, allowing to distribu
te operations and overlay results."
  (get-db [this db-id]))

(defprotocol IDatabase
  "A database recieves requests, runs these as actions and translates calls from these actions to db-op into calls to the storage backend."
  (db-action [this args]))

(defprotocol IDatastore
  (ds-op [this args]))

(defprotocol IGetDatastore
  (get-ds [this]))

(defn z
  "Executes a database action"
  ([args] (z @default-peer args))
  ([peer args]
     (let [db (get-db peer (:db args))]
       (db-action db args))))

(deftype ZeichnisPeer [databases]
  "Maintains a collection of logical databases."
  IZeichnisPeer
  (get-db [this db-id]
    (databases db-id)))

(defn fmap
  "Update all keys in map"
  [f m]
  (into (empty m) (for [[k v] m] [k (f v)])))

;; these are init multimethods for datastore and database config maps
(defmulti init-ds
  "Initialization functions for different kinds of physical datastores"
  :type)
(defmulti init-db
  "Initialization functions for different kinds of logical databases"
  (fn [conf dss] (:type conf)))

(defn init-peer
  "Initializes datastores and databases given configuration maps and returns an encapsulating peer object."
  [database-conf datastore-conf]
  (let [dss (fmap init-ds datastore-conf)
        dbs (fmap #(init-db % dss) database-conf)]
    (ZeichnisPeer. dbs)))

(defn init-default-peer
  "Sets the default peer with initialization. (the default peer is the one that the (z ..) function refers to if not instructed otherwise."
  ([db-conf ds-conf]
     (init-default-peer db-conf ds-conf false))
  ([db-conf ds-conf hard?]
     (if (or hard? (nil? @default-peer)) ; this is not entirely safe, but it's just meant to catch the stupid anyway
       (reset! default-peer (init-peer db-conf ds-conf))
       (throw (Exception. "Default peer should not be initialized more than once, try update-peer")))))

