;; Copyright (c) Philipp Meier (meier@fnogol.de). All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which
;; can be found in the file epl-v10.html at the root of this distribution. By
;; using this software in any fashion, you are agreeing to be bound by the
;; terms of this license. You must not remove this notice, or any other, from
;; this software.

(ns liberator.async
  (:require [clojure.core.async :as async :refer [go <!]]
            [clojure.core.async.impl.protocols :as async-p]))

(def channel?
  "Returns true if argument is a core.async channel"
  (partial satisfies? async-p/Channel))

(defmacro go?
  "A `go` block which will catch any thrown `Throwable` instances
and yield them as the value of the block channel."
  [& body]
  `(async/go
     (try
       ~@body
       (catch Throwable e# e#))))

(defn rethrow-exceptions
  [maybe-e]
  (if (instance? Throwable maybe-e)
    (throw maybe-e)
    maybe-e))

(defmacro <?
  "Read a value from the channel found by evaluating `expr` and, if it is
a `Throwable` instance, then throw it, otherwise return it. Must be used
inside a `go` block."
  [expr]
  `(rethrow-exceptions (async/<! ~expr)))

(defn async-response
  "If `r` has a `:body` which is a core.async channel, then returns that
channel, else returns `nil`."
  [r]
  (let [b (and r (:body r))]
    (when (channel? b)
      b)))

(defn async-middleware
  "Utility for constructing ring middleware out of a pair of `:request` and
`:response` handling functions (such as those found in ring core). Returns
a function which can be used to wrap handlers in the resulting middleware.
Note that neither `:request` and `:response` can itself return a core.async
channel; if you need to write async aware middleware then you don't need this
function!"
  [& {:keys [request response]
      :or {request identity response nil}}]
  (fn [handler]
    (fn [req]
      (let [resp (-> req request handler)]
        (if (and response (async-response resp))
          (update-in resp [:body] (partial async/map< response))
          resp)))))

(defmacro <val?
  "Evaluate `expr` and, if it is a channel, read a value from it with `<?`
and yield it as the result, otherwise return the value of `expr`.
Must be used inside a `go` block (at least when the result of `expr` is a
channel)."
  [expr]
  `(let [v# ~expr]
     (if (channel? v#)
       (<? v#)
       v#)))

(defmacro <let?
  "Optimistic mixed blocking and non-blocking version of `let`. Used to write
code which receives functions which may be either blocking or non blocking,
and needs to itself become either blocking or non blocking. This allows
liberator to optionally work with core.async without forcing it on clients,
and without requiring two sets of parallel APIs etc. (at the price of a
rather ugly macro).

Establishes `bindings` as if by `let`. If any of the resulting bindings
are a channel, then a `go` block is initiated, the results of any channel
bindings are read inside the block and rebound, and `body` is executed
in the scope of the resulting bindings.

If none of the resulting bindings from the first step are channels
then `body` is executed in the scope of these bindings (no go block)."
  [bindings & body]
  (let [bind-map (apply hash-map bindings)
        bind-keys (-> bind-map keys vec)
        bind-gs (vec (for [k bind-keys] (gensym)))]
    `(let [do-body# (fn ~bind-keys ~@body)
            ~@(mapcat (fn [gs k] [gs (get bind-map k)])
                      bind-gs bind-keys)]
       (if (or ~@(for [gs bind-gs] `(channel? ~gs)))
         (go?
          (<val?
           (do-body# ~@(for [gs bind-gs] `(<val? ~gs)))))
         (do-body# ~@bind-gs)))))
