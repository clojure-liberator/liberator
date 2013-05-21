---
layout: default
title: FAQ
---

# Frequently Asked Questions

### Why does liberator call post! after the handler function?

Short answer: it doesn't!

Long answer: most likely you did not the declare ````post!```` action
as a function but as a value. Due to the exact details of the
````defresource```` macro expansion, the form used as the value is
evaluated at unexpected times.

{% highlight clojure %}
(def x (atom 0))
(defresource wrong
  :post! (swap! x inc) ;; this is the bug
  :handle-ok (fn [_] (format "The counter is %d" @x)))
  
(defresource right
  :post! (fn [_] (swap! x inc) ;; this is right
  :handle-ok (fn [_] (format "The counter is %d" @x)))
{% endhighlight %}

If you want to understand the glory details, macroexpand both forms
and reason on the lexical scopes.

### Why are post requests refused with a 405?

Add ````:post```` to the list at ````:allowed-methods````

{% highlight clojure %}
(defresource post-me-harder 
  :allowed-methods [:get :post])
{% endhighlight %}

### Why aren't there more FAQ? Where can I get help?

There wheren't that many questions that came up frequently. To get
support quickly, please post your question to our
[Google Group](https://groups.google.com/forum/#!forum/clojure-liberator).
