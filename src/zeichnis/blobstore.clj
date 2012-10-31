
;; actions are higher level database tasks, ops are lower-level datastore directives
;; we externalize them as below (will still need to figure out whehter this is the right way to do it)
;; in order to enable extensibility of the instruction set

;; databases can redefine actions
;; datastores can as well
;; so in many cases we probably just re-distribute the same operations to
;; all datastores

(def blobstore-actions (atom {}))
(def blobstore-ops (atom {}))

(defprotocol IGetAction
  (get-action [this action-id]))
(defprotocol IGetDatastore
  (get-ds [this]))

(deftype SingleBlobStoreDB [datastore]
  IGetAction
  (get-action [this action-id]
    (@blobstore-actions action-id))
  IDatabase
  (db-action [this args]
    ((get-action this (:function args))  (:input args))))

;; signature for database actions is (action db args)

(defmacro act [db k args]
  `(zeichnis.core/db-action ~db {:function ~k :input ~args}))

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


; resolve-node as simple example

;; we need to get all the outgoing substitutions
;; and we need to find our own substitions (for the target term)
;; first of all our own substitutions
;; a) those that are subsets of our term
;; b) those children with an overlap (introduce new common ancestor)
;; c) substutions to the target term that are not covered anywhere (orphans)

;; for b) we need anti-unification

;; real simple example:
;; have a termstore that simply stores the set of terms
;; and we need to check every entry
