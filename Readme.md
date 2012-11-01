# Zeichnis - A universal term store

Zeichnis is a backend-agnostic database framework intended for storage and query of terms. In this context 'terms' roughly correspond to JSON documents. Zeichnis indexes these terms structurally and allows us to query and store underdefined terms (e.g. containing variables). This works much as it would in a Prolog-like system (though it slighly departs from classical Prolog semantics).

Zeichnis is originally intended as a distributed persistence layer for function memoization, dynamic programming (and even functional reactive pogramming). It is not (yet) a general purpose database. One of the possible uses of Zeichnis (with its emphasis on shared structure) will however be to allow for processing with persistent data structures (much as Clojure does locally) in a distributed manner.

### Status

Zeichnis is in early alpha stage. We are constantly developing it and there already is some documentation on the concepts going into Zeichnis in the [Wiki](https://github.com/werg/zeichnis/wiki). You can also find some basic usage below.

### Limitations

Zeichnis currently has no well-developed means of managing state. It is not a general purpose database!!
The ostensive semantic model of Zeichnis is that of an underdefined set of terms in which we constantly keep discovering new members. There is no sense of mutability, nor a built-in means of asserting or retracting facts that will change over time (temporally labeled facts are fine). Considering these characteristics Zeichnis is perfectly suited for memoizing pure functions.

We are examining how to extend Zeichnis using metadata in order to enable not only attribution and semantic annotation, but also state and (partially ordered) transactions (i.e. much of the features reported missing above). This is an ongoing investigation.

### Zeichnis and Datomic

Zeichnis is in many ways inspired by Datomic (backend-agnostic, graph-like, emphasis on immutability and managed mutability as well as an affinity for logic-programming) it does however differ in several significant points. The obvious one is that Zeichnis has no central transactor. From that we lose linearly ordered transactions and the capability to have the whole database as a single value. We win write-scale.

The semantics of Zeichnis differ significantly. Zeichnis does not index a graph, it uses a graph to index. In fact, Zeichnis does not fundamentally presuppose any specific form of indexing. What it shares is that it has NoSQL-type scale without sacrificing complex indexing and querying, by embracing immutabiltiy.


### Implementation

Zeichnis uses term-indexing strategies akin to classic Prolog. This makes it the only database we know of capable of storing underdefined data. Zeichnis uses structure-sharing wherever possible. (Check out the Wiki for more on the implementation side of things.)

## Usage

Interactions with Zeichnis go through one function `(z {...})` which takes one argument. This one argument usually should be a function memoization structure according to the scheme `{:function _ :input _}` in order to make calls to Zeichnis easily serializable in Zeichnis.

```clojure
(use 'zeichnis.core 'zeichnis.setstore)

(def database-conf {:my-db {:type 'SingleSetStoreDB 
                            :conf {:datastore :my-ds}}})
(def datastore-conf {:my-ds {:type 'SetStore}})
(init-default-peer database-conf datastore-conf)

(z {:db :my-db :function :store-term :input {:bucket "default" :content {:a 1}}})

(z {:db :my-db :function :is-stored? :input {:bucket "default" :content {:a 1}}})
(z {:db :my-db :function :is-stored? :input {:bucket "default" :content {:a 2}}})

(z {:db :my-db :function :store-term :input {:bucket "default" :content {:a 2}}})

(z {:db :my-db :function :all-subsumed :input {:bucket "default" :content {:a '_}}})
```

Zeichnis' current subsumption model allows for _extension_ next to classical substitution. Extension for uniquely vertex-labeled terms means adding further children to any node (substructure) in the term. This amounts to an effect similar to having a tail variable in Prolog lists, only applied to hash maps and generalized to include an extension to any further index/key one would add.

As an example: `{:a _}` subsumes both `{:a 1}` and `{:a _ :b 5}`.

```clojure
(z {:db :my-db :function :store-term :input {:bucket "default" :content {:a _ :b 5}}})

(z {:db :my-db :function :all-subsumed :input {:bucket "default" :content {:a '_}}})
```

This also demonstrates storing underdetermined content.



## License

Copyright © 2012 White Paper Analytics Limited

Distributed under the Eclipse Public License.
