(ns zeichnis.core)

(defn -main
  "I don't do a whole lot."
  [& args]
  (println "Hello, World!"))

(defprotocol Term
  "Well, a term."
  (subsumers [this] "All terms subsuming this."))

(defprotocol Zerializable
  "Objects serializable for use in Zeichnis."
  (zerialize [this] "Serialize a term for use in zeichnis."))

(defprotocol Blobstore
  "Basic key-value storage API for Zeichnis (for stores without bells & whistles)."
  (put-blob [this key blob] "Store a blob without concurrency management.")
  (get-blob [this key] "Retrieves a blob.")
  (delete-blob [this key] "Delete a blob without concurrency management."))

(defprotocol ConcMan
  "Concurrency Manager for transactions, adding and deleting objects"
  (update-ref [this key updater]  "Update a ref using concurrency resolution."))
;; here updater is a function which acts a lot like a reduce, taking an existing
;; oject and doing something to it (presumably adding/deleting)
;; refs work like in clojure, so it's just a single point of mutable state,
;; a pointer to an immutable blob object
;; how to make these updates concurrency-safe is a matter specific to storage-backend

;; below should be a type and the two behaviors should be two different protocols
;; TODO
(defprotocol ZeichnisPeer
  (store-term [this term] "Stores a term.")
  (query-term [this term] "Retrieves contents for a term."))

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
