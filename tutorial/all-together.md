---
layout: tutorial
title: Tutorial - All together now
---
# All together now

Knowing how to handle different request methods and the extension
points for a liberator resource is one thing, putting all together in
a un-complected way is not trivial. Fortunately it's not hard either
as the following example shows.

## Handling a collection of resources

A typical model when you want to make some entities available is to
use one resource for the collection of entities and a second resource
which represents a single entity. This maps perfectly with the
semantics of GET, PUT, POST and DELETE:

|Resource|Method|Comment          |
|--------|------|-----------------|
|list    |GET   |List of entities |
|list    |POST  |Create new entity|
|entry   |GET   |Get entity       |
|entry   |DELETE|Delete entity    |
|entry   |PUT   |Replace entity   |

## List resource

The list resources accept and produces application/json in this
example. The body is parsed in ````:malformed```` and stored in the
context under the key ````::data````. To keeps things simple
````post!```` generates a random number for the id and stores id under
````:id````. We enable redirect after post and ````:location```` picks
up the id to create the url where a resource for the created entity
can be found.

In case of a GET we return a simple list of URLs pointing to resources
for all entries.

First come some helper functions which might some day find it's way
into liberator.

{% highlight clojure %}

;; convert the body to a reader. Useful for testing in the repl
;; where setting the body to a string is much simpler.
(defn body-as-string [ctx]
  (if-let [body (get-in ctx [:request :body])]
    (condp instance? body
      java.lang.String body
      (slurp (io/reader body)))))

;; For PUT and POST parse the body as json and store in the context
;; under the given key.
(defn parse-json [context key]
  (when (#{:put :post} (get-in context [:request :request-method]))
    (try
      (if-let [body (body-as-string context)]
        (let [data (json/read-str body)]
          [false {key data}])
        {:message "No body"})
      (catch Exception e
        (.printStackTrace e)
        {:message (format "IOException: %s" (.getMessage e))}))))

;; For PUT and POST check if the content type is json.
(defn check-content-type [ctx content-types]
  (if (#{:put :post} (get-in ctx [:request :request-method]))
    (or
     (some #{(get-in ctx [:request :headers "content-type"])}
           content-types)
     [false {:message "Unsupported Content-Type"}])
    true))

{% endhighlight %}

Then comes the resource for the list of entries.

{% highlight clojure %}
;; we hold a entries in this ref
(defonce entries (ref {}))

;; a helper to create a absolute url for the entry with the given id
(defn build-entry-url [request id]
  (URL. (format "%s://%s:%s%s/%s"
                (name (:scheme request))
                (:server-name request)
                (:server-port request)
                (:uri request)
                (str id))))


;; create and list entries
(defresource list-resource
  :available-media-types ["application/json"]
  :allowed-methods [:get :post]
  :known-content-type? #(check-content-type % ["application/json"])
  :malformed? #(parse-json % ::data)
  :post! #(let [id (str (inc (rand-int 100000)))]
            (dosync (alter entries assoc id (::data %)))
            {::id id})
  :post-redirect? true
  :location #(build-entry-url (get % :request) (get % ::id))
  :handle-ok #(map (fn [id] (str (build-entry-url (get % :request) id)))
                   (keys @entries)))
{% endhighlight %}

## Entry resource

The entry-resource implements access to a single entry. It supports
GET, PUT and DELETE. Like the list-resource it accepts json for update
and generates a json response.

An entries exists if the stored value is not nil. If the stored value
is nil, the entry is gone (status 410). If there is no value stored
at all, the entries does not exist (status 404).

On delete, the entry is set to nil and thus marked as gone.

Put requests replace the current entry and are only allowed if the
entries exists (````:can-put-to-missing````). The function for
````:handle-ok```` might surprise at first sight: the keyword
````::entry```` is used as a function and will lookup itself in the
context.

{% highlight clojure %}
(defresource entry-resource [id]
  :allowed-methods [:get :put :delete]
  :known-content-type? #(check-content-type % ["application/json"])
  :exists? (fn [_]
             (let [e (get @entries id)]
                    (if-not (nil? e)
                      {::entry e})))
  :existed? (fn [_] (nil? (get @entries id ::sentinel)))
  :available-media-types ["application/json"]
  :handle-ok ::entry
  :delete! (fn [_] (dosync (alter entries assoc id nil)))
  :malformed? #(parse-json % ::data)
  :can-put-to-missing? false
  :put! #(dosync (alter entries assoc id (::data %)))
  :new? (fn [_] (nil? (get @entries id ::sentinel))))
{% endhighlight %}

Here we use the syntax to define parametrized resources:
````(defresource entry-resource \[id\])````, these go
hand-in-hand with compojure's routing parameters:

{% highlight clojure %}
(defroute collection-example
    (ANY ["/collection/:id" #".*"] [id] (entry-resource id))
    (ANY "/collection" [] list-resource))
{% endhighlight %}

## Possible extensions

This example is far from being feature complete. It can be extended
to support conditional requests and more media types. You can use
````authorized?```` to restrict access to the resourses.

