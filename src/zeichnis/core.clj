(ns zeichnis.core
  (:require [clojure.set :as cset]))

(defmulti resolve-node :store-id
  "Node accessors take the form {:store-id _ :node-id _}
   where :store-id and :node-id can be arbitrary serializable terms or tokens.")

(defprotocol BlobStore
  "Basic key-value storage API for Zeichnis.

  Suitable for storage backends without bells & whistles."
 (put-blob [this key blob] "Store a blob.")
  (get-blob [this key] "Retrieve a blob.")
  (delete-blob [this key] "Delete a blob."))

(deftype MapBlobStore [store]
  (put-blob [this key blob] "Store a blob."
    (swap! store assoc key blob))
  (get-blob [this key] "Retrieve a blob."
    (get @store key))
  (delete-blob [this key] "Delete a blob."
    (swap! store dissoc key)))

(defn map-blobstore
  ([] (map-blobstore {}))
  ([start-store]
     (MapBlobStore (atom start-store))))

(defprotocol Subsumer
  "Basic behavior or something that when given a term can provide substitutions leading to that term."
  (subsume [this term] [this term path-prefix]
    "Return a map of paths to substituted terms, if a path-prefix is provided all paths are appended to it."))

(defn subsume* [prototype term path-prefix]
  "Generic subsume operation returns a map of paths-to-values (so under this path we substitute that).

   If possible relies on polymorphic sipatch of the Subsumer protocol."
  (if (= prototype term)
    {} ; since they are the same, there is nothing to substitute
    (if (satisfies? Subsumer prototype)
      (subsume prototype term path-prefix)
      nil)))

(extend-type clojure.lang.Symbol
  Subsumer
  (subsume
    "Here we implement subsumption for symbols.

     It is assumed that a symbol will subsume any term, simply by sybstituting to that term."
    ([this term]
       (subsume this term []))
    ([this term path-prefix]
       (if (nil? term)
         nil
         {path-prefix term}))))

(defn subsume-keys [prototype term parent-keys child-keys path-prefix]
  "For a structural element with keys (so either map or sequence), compute substitutions recursively."
  (if (or (= parent-keys child-keys) (cset/subset?  parent-keys child-keys))
    (let [ext-keys (cset/difference (set child-keys) (set parent-keys))
          exts (zipmap (map conj (repeat path-prefix) ext-keys)
                       (map get (repeat term) ext-keys))
          substs (loop [substs {} parent-keys parent-keys]
                   (if (empty? parent-keys)
                     substs
                     (let [pk (first parent-keys)]
                       (if-let [subst (subsume*
                                       (get prototype pk)
                                       (get term pk)
                                       (conj path-prefix pk))]
                         (recur (merge substs subst) (rest parent-keys))
                         nil))))]
          (if (nil? substs)
            nil
            (merge exts substs)))))

(extend-type clojure.lang.IPersistentMap
  Subsumer 
 (subsume
    ([this term]
       (subsume this term []))
    ([this term path-prefix]
       (let [parent-keys (keys this)
             child-keys (if (map? term)
                          (keys term)
                          (range (count term)))]
         (subsume-keys this term parent-keys child-keys path-prefix)))))

;;todo add extra path
;; todo: what was this todo about?

(defn find-subsumers [prototypes term]
  "Maybe a slightly misguiding function, but which exemplifies the subsumption operation.
   Find, in a collection of terms, those which subsume a given term."
  (loop [subsumers '() prototypes prototypes]
    (if (empty? prototypes)
      subsumers
      (let [p (first prototypes)
            s (subsume p term)]
        (if (nil? s)
          (recur subsumers (rest prototypes))
          (recur (conj subsumers {:subsumer p :substs s})
                 (rest prototypes)))))))


(comment
  (zeichnis.core/find-subsumers [{:a '_} {:a {}} {:b 5}] {:a {:c 1}})
  =>
   ([{:a {}} {[:a :c] 1}] [{:a _} {[:a] {:c 1}}]))

;; how do i do metadata?
;; especially, how do i do membership??

(defprotocol Node
  "Represents the connection to a node in some storage-backend.
   Note that no mutability management is provided or implied,
   mutability management depends on the storage backend and requirements of the peer."
  (get-content [this])
  (add-link-in* [this label source-node] [this label source-node annotation])
  (add-link-out* [this label target-node] [this label target-node annotation])
  (follow-links [this label] [this label annotation])
  ())

;; i'm not quite happy with the way links are done here.
;; would prefer to do link adding external to nodes

(defn make-link
  ([source-node label target-node]
     (add-link-out* source-node label target-node)
     (add-link-in* target-node label source-node))
    ([source-node label target-node annotation]
     (add-link-out* source-node label target-node annotation)
     (add-link-in* target-node label source-node annotation)))

(defprotocol NodeStore
  (store [this content]
    "Creates a new node representing the supplied content and returns it,
     integrating it into the indexing scheme (e.g. partial order)."))

(defprotocol PartialOrder
 ; (all> [this item])
  (any> [this item])
 ; (all< [this item])
 ; (any< [this item])
 ; (has? [this item])
  )


;; how about making everything callback- and update-based?
;; the semantics is, i have a program and want to decide whether i need to recom


;; i guess dealing with the fact that queries are fucking stateful and the database can
;; change must be delegated to the processing layer which we will build on top

;; for now 


;; maybe create a TermNode
;; how to make nodes address each other across backends?
;; so we need a shared zeichnis-peer

;; we need special syntax for links in terms... ah, it's just symbols
;; and then metadata
;; and we can add symbol names in the metadata as well

;; we have two different kinds of metadata 1) metadata in terms, about their contents
;; 2) metadata about terms. can this be unified?

;; the simplest way to do metadata in terms, is to have the term
;; and then a collection of paths, which are then annotated with metadata-terms

;; A termstore
;; a way to check whether something is actually stored
;; a way to add/insert
;; a way to find subsumers