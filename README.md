Liberator Documentation
=======================

Liberator is a Clojure library for building RESTful applications.

This **gh-pages** branch of the liberator repository contains the GitHub pages website and user documentation for Liberator, which you can see at [Liberator's Website](http://clojure-liberator.github.io/liberator/).

## Contributing Small Fixes and Updates

The Liberator team tries to live an open development model, embracing contribution by others. Currently there are commits by about 10 other developers. We love how Github makes it dead easy to contribute event a one liner fix.

If you'd like to contribute to the liberator website and documentation, follow these easy steps:

1. Go to the Liberator GitHub repo and switch the branch with the drop down to the gh-pages branch (it's an orphan branch with a jekyll project if you are curious). Or you can go directly to it [here](https://github.com/clojure-liberator/liberator/tree/gh-pages).

2. Find the document you want to update, and click on it.

3. Click "Edit" on the toolbar, and your view of the tutorial markdown source will switch to a text editor, it has 2 views, Code and Preview. Make your changes using [Markdown](http://daringfireball.net/projects/markdown/syntax) and preview them.

4. When you are satisfied, type some nice notes describing what you changed and why in the commit summary and description portion. Press the "Propose File Change" button.

5. Rejoice in how thankful everyone is that you've made Liberator better, and in how much easier open source has gotten. Emailing a patch file? Puh-lease.

## Making More Significant Contributions

If you'd like to make more significant enhancements to the documentation or website, please fork [Liberator on GitHub](https://github.com/clojure-liberator/liberator). You can then submit your pull requests to the gh-pages branch if you'd like to contribute back your enhancements. 

### Host the Website Locally

Most users should just use [Liberator's Website](http://clojure-liberator.github.io/liberator/) to access the docs, but if you're contributing to Liberator's documentation, or if you want your own local copy, you'll need to build them.

Liberator's website is hosted by GitHub as GitHUb Pages, which are powered by [jekyll](http://jekyllrb.com/). To build and serve the documentation website, you'll need a few things:

* [Ruby](https://www.ruby-lang.org/) - many Linux or Mac systems will already have Ruby
* [RubyGems](http://rubygems.org/) - many Ruby installs will already have RubyGems

After forking the repository, host the website in a local copy of the fork:
 
```console
gem install redcarpet
gem install jekyll

git checkout gh-pages

jekyll serve --baseurl '' --port 3000
```

In another terminal, open your browser on the local copy of the site:

```console
open http://localhost:3000
```

### Update the Site

Edit the source markdown files, then build the updates once:

```console
jekyll build
```

Or have jekyll watch the files for your updates and build them continuously:

```console
jekyll build --watch
```
