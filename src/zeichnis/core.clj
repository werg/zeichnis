(ns zeichnis.core)

(defn -main
  "I don't do a whole lot."
  [& args]
  (println "Hello, World!"))

(defprotocol Term
  "Well, a term."
  (subsumers [this] "All terms subsuming this."))

(defprotocol Zerializable
  "Objects serializable for use in Zeichnis.

   This serialization format should account for free places/slots (i.e. variables)
   in a uniform manner, so equivalent structures always should be serialized the same
   indiscriminately of variable names or incidental ordering.
  "
  (zerialize [this] "Serialize a term for use in zeichnis."))

(defprotocol Blobstore
  "Basic key-value storage API for Zeichnis.

  Suitable for storage backends without bells & whistles."
  (put-blob [this key blob] "Store a blob.")
  (get-blob [this key] "Retrieve a blob.")
  (delete-blob [this key] "Delete a blob."))

(defprotocol ConcMan
  "Concurrency Manager for updating refs.

   Should encapsulate storage-backend specific logic to enable concurrency-safe
   updates of refs. Refs work like in Clojure, i.e. they are a single point of
   mutability, implemented as a malleable pointer to an immutable blob.

   How to make these updates concurrency-safe is a matter specific to
   storage-backend. If a datastore offers vector clocks or strong consistency,
   usage is advised. Alternatively a redundancy scheme with randomization may be used.
   "
  (update-ref [this key updater]
    "Update a ref using concurrency resolution.
     Takes as argument the key of the ref, as well as a function mapping
     current value to updated value of ref (so updater often would act very much like
     a reducer with the to-be-added element already curried.
     In case of concurrent writes, updater may be called repeatedly
     and should be idempotent.

     An implementation of update-ref should then store the updated value and switch
     the ref's pointer, ensuring consistency w.r.t. concurrent writes.
    "))


;; below should be a type and the two behaviors should be two different protocols
;; TODO
(defprotocol ZeichnisPeer
  (store-term [this term] "Stores a term.")
  (query-term [this term] "Retrieves contents for a term."))


;; so maybe we should separate the peer (maintaining all infrastructure-shit)
;; from different datastore-objects (which basically are partial orders)
;; [btw. should these be mutable?? -- maybe not??, or maybe every partial order
;; is kinda throw-away, so only available for one query, and then you have to
;; run back and get a new one, or you need to implement a listener-system]?

;;* how to modify this to give us a partial order?
;;  because that is basically what we want?
;;* though more generally or fundamentally we could
;;  use a model of retrieving metadata about objects


;; so every index is a partial order
;; and it is implemented on top of a metadata-about-blobs system?

;; maybe we need to do it simple first and then find the abstractions


;; before i had another signature [this term options] ...
;; hmmm options
;; i kinda like the idea of encapsulating options in objects
;; and having function/method calls option-less, but we'll see about that

;; query-term is really opaque, gotta think about that one
;; but it means that we could retrieve anything, like subsumers, or subsumed
;; or what blob it's in
;; or what metadata it has and those options are matter of what object
;; we're sending the query to
;; not sure whether this is an unencumbering abstraction

;; there can be many different kinds of links (and indexing thingies)
;; naming schemes (so like implicitly linked blobs, linked by name like *_links)

;; transactions

;; metadata

;; notifications

;; we don't delete terms, we delete blobs. (so we garbage-collect them).


;; all of this is just term storage, we don't yet talk about processing
