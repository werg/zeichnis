(ns zeichnis.blobstore
  (:use [zeichnis.subsume]
        [zeichnis.core]))

;; this file contains a first draft of how a zeichnis-database could be constructed against a mutable k-v store
;; metadata is still elided

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
        from-term  (:content from)
        our-subst (subsume from-term target-term)]
    (when (not (empty? our-subst)) ;; i.e. we are actually a child (and not the same as from)
      (let [has-parent? (atom false)]
        (doseq [{:keys [neighbor annot]} (act db :follow-links
                                              {:node from
                                               :label :subst
                                               :direction :out})]
          ;; annot contains all the annotation stuff
          (let [{:keys [inter diff1 diff2]} (compare-substs our-subst annot)]
            (when (and inter (not (empty? (subsume from-term inter))))
              (swap! has-parent? #(or % true))
              (if (and (empty? diff1) (not (empty? diff2)))
                ;; check that we're not the same as neighbor
                (act db :route-through {:old-parent from
                                        :new-parent target
                                        :child neighbor})
                ;; TODO: there is a problem here, if we add target as immediate child of from
                ;; we risk, in later stages, if we find something that has a common ancestor,
                ;; having excess links going in
                ;; but maybe this only happens if we're updating concurrently?
                (if (empty? diff2)
                  (act db :insert-from {:from neighbor
                                        :target target})
                  (let [inter-spec {:content (apply-subst  (:content from) inter)
                                    :bucket (:bucket from)}]
                    (act db :route-through {:old-parent from
                                            :new-parent inter-spec
                                            :child neighbor})
                    (act db :insert-node inter-spec)
                    (act db :insert-from {:from inter-spec
                                          :target target})))))))
        (when (not @has-parent?)
          (act db :make-link {:from from
                              :to target
                              :label :subst
                              :annot our-subst}))))))

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

(defn store-term [db args]
  (act db :insert-node args)
  (act db :mark-stored args))

(defn ds-is-stored? [ds {:keys [bucket content]}]
  (let [flag (get-in ds [bucket content :stored?])]
    (if (nil? flag)
      false
      flag)))

(defn is-stored? [db args]
  (ds-is-stored? (get-ds db) args))

;; go through all children
;; if they subsume our term, descend into them
;; if our term subsumes them, add them to result and descend into them
(defn all-children-subsumed [db children subsumer]
  (concat (filter #(subsume subsumer (:content %)) children)
          (mapcat #(act db :all-subsumed-from {:from %
                                               :subsumer subsumer})
                  (filter #(subsume (:content %) subsumer) children))))

(defn all-subsumed-from [db {:keys [from subsumer]}]
  (let [children (map :child (act db
                      :follow-links {:node from
                                     :label :subst
                                     :direction :out}))]
    (all-children-subsumed db children subsumer)))

(defn all-subsumed [db subsumer]
  (all-children-subsumed db [(act db :get-root subsumer)] subsumer))

(defn- link-keypath [node-spec direction label]
  [(:bucket node-spec) (:content node-spec) :links direction label])

(defn ds-follow-links [ds {:keys [direction node label]}]
  ;; paste the child, which was the key into a {:child .. :label ... :annot} structure
  (map #(hash-map :annot (second %) :neighbor (first %) ) (get-in ds (link-keypath node direction label))))

(defn ds-has? [ds {:keys [bucket content]}]
  (not (nil? (get-in ds [bucket content]))))

(defn follow-links [db args]
  (ds-follow-links (get-ds db) args))

(defn has? [db args]
  (ds-has? (get-ds db) args))

(defn- default-map-conj [hm arg]
  (if (nil? hm)
    (conj {} arg)    (conj hm arg)))

(defn make-link [ds {:keys [from to label annot]}]
  (let [new-ds (update-in ds (link-keypath from :out label) default-map-conj [to annot])]
    (update-in new-ds (link-keypath to :in label) default-map-conj [from annot])))

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

(defn mark-stored [ds {:keys [bucket content]}]
  (assoc-in ds [bucket content :stored?] true))

(swap! blobstore-actions merge {:insert-node insert-node
                                :insert-from insert-from
                                :route-through route-through
                                :get-root get-root
                                :follow-links follow-links
                                :has? has?
                                :store-term store-term
                                :is-stored? is-stored?
                                :all-subsumed all-subsumed
                                :all-subsumed-from all-subsumed-from})

(swap! blobstore-ops merge {:make-link make-link
                            :remove-link remove-link
                            :make-node make-node
                            :has? has?
                            :mark-stored mark-stored})