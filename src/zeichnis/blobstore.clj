(ns zeichnis.blobstore
  (:use [zeichnis.subsume]
        [zeichnis.core]))
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
      (action this (:input args))
      ;; todo: insert get-datastore
      (ds-op datastore args)))
  IGetDatastore
  (get-ds [this]
    (get-ds datastore)))

(deftype MapBlobStore [hm]
  IDatastore
  (ds-op [this args]
    (let [{:keys [input function]} args]
      (prn hm function input)
      (swap! hm (@blobstore-ops function) input)))
  IGetDatastore
  (get-ds [this]
    @hm))

(defmethod init-db 'SingleBlobStoreDB [conf dss]
  (SingleBlobStoreDB.  (dss (:datastore (:conf conf)))))

(defmethod init-ds 'MapBlobStore [conf] (MapBlobStore. (atom {})))

;; signature for database actions is (action db args)

(defmacro act [db k args]
  `(zeichnis.core/db-action ~db {:function ~k :input ~args}))

(defn insert-node [db args]
  "insert a node specified in the format {:bucket _ :content _} into the bucket. does not store!"
  (let [{:keys [bucket content]} args]
    (act db :insert-from {:target args
                          :from (act db :get-root {:bucket bucket})})))

(defn insert-from [db args]
  (let [{:keys [from target]} args
        target-term (:content target)
        our-subst (subsume (:content from) target-term)]
    (let [has-parent? (atom false)]
      (doseq [{:keys [child annot]} (act db :follow-links
                                         {:node target-term
                                          :label :subst
                                          :direction :out})]
        ;; annot contains all the annotation stuff
        (let [{:keys [inter diff1 diff2]} (compare-substs our-subst annot)]
          (when inter
            (swap! has-parent? #(or % true))
            (if (empty? diff1)
              (act db :route-through {:old-parent from
                                      :new-parent target
                                      :child child})
              ;; TODO: there is a problem here, if we add target as immediate child of from
              ;; we risk, in later stages, if we find something that has a common ancestor,
              ;; having excess links going in
              ;; but maybe this only happens if we're updating concurrently?
              (if (empty? diff2)
                (act db :insert-from {:from-spec child
                                      :target target})
                (let [inter-spec {:content (apply-subst  (:content from) inter)
                                  :bucket (:bucket from)}]
                  (act db :route-through {:old-parent from
                                          :new-parent inter-spec
                                          :child child})
                  (act db :insert-node inter-spec)
                  (act db :insert-from {:from inter-spec
                                        :target target})))))))
      (if (not @has-parent?)
        (act db :make-link {:from from
                            :to target
                            :label :subst
                            :annot our-subst})))))

(defn get-root [db args]
  (let [root-spec (merge args {:content '_})]
    (act db :make-node root-spec)
    root-spec))

(defn route-through [db args]
  (let [{:keys [old-parent new-parent child]} args]
    (act db :make-link {:from new-parent
                        :to child
                        :label :subst
                        :annot (subsume (:content new-parent)
                                        (:content child))})
    (act db :make-link {:from old-parent
                        :to new-parent
                        :label :subst
                        :annot (subsume (:content old-parent)
                                        (:content new-parent))})
    (act db :remove-link {:from old-parent :to child :label :subst})))

(defn- link-keypath [node-spec direction label]
  [(:bucket node-spec) (:content node-spec) :links direction label])

(defn ds-follow-links [ds {:keys [direction node label]}]
  (get-in ds (link-keypath node direction label)))

(defn ds-has? [ds {:keys [bucket content]}]
  (not (nil? (get-in ds [bucket content]))))

(defn follow-links [db args]
  (ds-follow-links (get-ds db) args))

(defn has? [db args]
  (ds-has? (get-ds db) args))

(defn- default-map-conj [hm arg]
  (if (nil? hm)
    (conj {} arg)
    (conj hm arg)))

(defn make-link [ds {:keys [from to label annot]}]
  (update-in ds (link-keypath from :out label) default-map-conj [to annot])
  (update-in ds (link-keypath to :in label) default-map-conj [from annot]))

(defn dissoc-in
  "Dissociates an entry from a nested associative structure returning a new
  nested structure. keys is a sequence of keys. Any empty maps that result
  will not be present in the new structure."
  [m [k & ks :as keys]]
  (if ks
    (if-let [nextmap (get m k)]
      (let [newmap (dissoc-in nextmap ks)]
        (if (seq newmap)
          (assoc m k newmap)
          (dissoc m k)))
      m)
    (dissoc m k)))

(defn remove-link [ds {:keys [from to label]}]
  (dissoc-in ds (conj (link-keypath from :out label) to))
  (dissoc-in ds (conj (link-keypath to :in label) from)))

(defn make-node [ds args]
  (let [{:keys [bucket content]} args]
    (if (ds-has? ds args)
      ds
      (assoc-in ds [bucket content :links] {:in {} :out {}}))))

(swap! blobstore-actions merge {:insert-node insert-node
                                :insert-from insert-from
                                :route-through route-through
                                :get-root get-root
                                :follow-links follow-links
                                :has? has?})

(swap! blobstore-ops merge {:make-link make-link
                            :remove-link remove-link
                            :make-node make-node
                            :has? has?})

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