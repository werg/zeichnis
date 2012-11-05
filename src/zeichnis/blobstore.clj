(ns zeichnis.blobstore
  (:use [zeichnis.subsume]))
;; databases can feature their own implementations of actions

;; in many cases we probably just re-distribute the same operations to
;; all backing datastores

(def blobstore-actions (atom {}))
(def blobstore-ops (atom {}))

(defprotocol IGetAction
  (get-action [this action-id]))

(deftype SingleBlobStoreDB [datastore]
  IGetAction
  (get-action [this action-id]
    (@blobstore-actions action-id))
  IDatabase
  (db-action [this args]
    (if-let [action (get-action this (:function args))]
      (action  (:input args))
      ((@blobstore-ops (:function args)) datastore (:input args)))))

(defmethod init-db 'SingleBlobStoreDB [conf dss]
  (SingleBlobStoreDB.  (dss (:datastore (:conf conf)))))

(defmethod init-ds 'MapBlobStore [conf] (atom {}))

;; signature for database actions is (action db args)

(defmacro act [db k args]
  `(zeichnis.core/db-action ~db {:function ~k :input ~args}))

(defmacro trans [k args]
  `{:function ~k :input ~args})

(defn insert-node [db args]
  "insert a node specified in the format {:bucket _ :content _} into the bucket. does not store!"
  (let [{:keys [bucket content]} args]
    (act db :insert-from {:target args
                          :from (act db :get-root {:bucket bucket})})))

(defn insert-from [db args]
  (let [{:keys [from target]} args
        target-term (:content target)
        our-subst (subsume (:content from) target-term)]
    (doseq [{:keys [child annot]} (act db :follow-links-out
                                        {:node target-term
                                         :label :subst})]
      ;; annot contains all the annotation stuff
      (let [{:keys [inter diff1 diff2]} (compare-substs our-subst annot)]
        (if inter
          (if (empty? diff1)
            (act db :route-through {:old-parent from
                                    :new-parent target
                                    :child child})
            (if (empty? diff2)
              (act db :insert-from {:from-spec child
                                    :target target})
              (let [inter-spec {:content (apply-subst  (:content from-spec) inter)
                                :bucket (:bucket from-spec)}]
                (act db :make-node inter-spec)
                (act db :route-through {:old-parent from
                                        :new-parent inter-spec
                                        :child child})
                (act db :insert-node inter-spec)
                (act db :insert-from {:from inter-spec
                                      :target target})))))))))

(defn route-through [db args]
  (let [{:keys [old-parent new-parent child]} args]
    (act db :make-link {:from new-parent :to child})
    (act db :make-link {:from old-parent :to new-parent})
    (act db :remove-link {:from old-parent :to child})
    ))


(defn make-link [ds args]
  (swap! ds ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; schnittmenge:
;; create node for schnittmenge
;; do :route-through-ancestor on both 
;;   link parent to schnittmenge
;;   link target and schnittmenge with difference
;; insert new schnittmenge node from the top

;; no schnittmenge:
;; leave as it is
;; if we have a complete subset:
;; either descend (i.e. :insert-from)
;; or do :route-through-ancestor
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;; route through ancestor (old-parent new-parent child)
;; insert new-parent -- child link
;; insert old-parent -- new-parent link
;; remove old-parent -- child link
;; possibly route other parents through new parent!!! (no, since we insert from the top we're fine)



; resolve-node as simple example


;; we need to get all the outgoing substitutions
;; and we need to find our own substitions (for the target term)
;; first of all our own substitutions
;; a) those where all their paths leading on are contained or sub-paths of our term -- we're a child of theirs
;; b) those children with an overlap (either we are subpath of theirs, or they of ours, introduce new common ancestor with the overlap and sub-paths)
;; c) substutions to the target term that are not covered anywhere (orphans)

;; for b) we need anti-unification



;; :store term entails
;; :insert-node
;; :add-meta that it's stored


;; :add-meta entails
;; :insert-node with the metadata
;; (into its own kind of index) -- [:meta-bucket is optional?]

;; meta-thingy is for:
;; attribution
;; transaction
;; time information
;; providing the surface representation

;; the content of it is {:stored }_