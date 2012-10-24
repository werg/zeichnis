# Zeichnis - A universal term store

Zeichnis is a backend-agnostic database intended for storage and query of terms. In this context 'terms' roughly correspond semantically to JSON documents. Zeichnis indexes these terms structurally and allows us to query and store underdefined terms (e.g. containing variables). This works much as it would in a Prolog-like system (though it slighly departs from classical Prolog semantics).

Zeichnis is originally intended as a distributed persistence layer for function memoization, dynamic programming (and even functional reactive pogramming). It is not (yet) a general purpose database.

### Limitations

Zeichnis currently has no well-developed means of managing state. It is not a general purpose database!!
The ostentative semantic model of Zeichnis is that of an underdefined set of terms in which we constantly keep discovering new members. There is no sense of mutability, nor a built-in means of asserting or retracting facts that will change over time (temporally labeled facts are fine). Considering these limitations Zeichnis is perfectly suited for memoizing pure functions.

We are examining how to extend Zeichnis using metadata in order to enable not only attribution and semantic annotation, but also state and (partially ordered) transactions (i.e. much of the features reported missing above). This is an ongoing investigation.

### Zeichnis and Datomic

Zeichnis is in many ways inspired by Datomic (backend-agnostic, graph-like, emphasis on immutability and managed mutability as well as an affinity for logic-programming) it does however differ in several significant points. The obvious one is that Zeichnis has no central transactor. From that we lose linearly ordered transactions and the capability to have the whole database as a single value. We win write-scale.

The semantics of Zeichnis differ significantly. Zeichnis does not index a graph, it uses a graph to index. In fact, Zeichnis does not fundamentally presuppose any specific form of indexing. What it shares is that it has NoSQL-type scale without sacrificing complex indexing and querying, by embracing immutabiltiy.


### Implementation

Zeichnis uses term-indexing strategies akin to classic Prolog. This makes it the only database we know of capable of storing underdefined data. Zeichnis uses structure-sharing wherever possible. (Check out the Wiki for more on the implementation side of things.)

## Usage

So far Zeichnis is not in a usable state, we are still defining the basic API.

## License

Copyright © 2012 White Paper Analytics Limited

Distributed under the Eclipse Public License, the same as Clojure.
