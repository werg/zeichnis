(ns  zeichnis.setstore
  (:use [zeichnis.core]
        [zeichnis.subsume]))

(def set-store-actions (atom  {}))

(deftype SingleSetStoreDB [datastore]
  IDatabase
  (db-action [this args]
    (prn this args)
    ((@set-store-actions (:function args)) this (:input args)))
  IGetDatastore
  (get-ds [this]
    datastore))

(defn store-term [db args]
  ; maybe convert to canonical form
  (swap! (get-ds db) #(assoc % (:bucket args) (conj  (get % (:bucket args) #{}) (:content args)))))

(defn is-stored? [db {:keys [bucket content]}]
  ; maybe convert to canonical form?
  (contains? (@(get-ds db) bucket) content))

(defn all-subsumed [db {:keys [content bucket]}]
  (filter subsume (repeat content) (@(get-ds db) bucket) ))


(swap! set-store-actions merge {:store-term store-term :is-stored? is-stored? :all-subsumed all-subsumed})


(defmethod init-db 'SingleSetStoreDB [conf dss] (SingleSetStoreDB.  (dss (:datastore (:conf conf)))))
(defmethod init-ds 'SetStore [conf] (atom {}))
