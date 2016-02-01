# [DEPRECATED]

This tool is deprecated. Please follow [this guide](https://devcenter.heroku.com/articles/building-a-heroku-add-on) when building a Heroku add-on.

## `kensa create my_addon --template clojure`

This app will be used by Heroku's kensa gem as the skeleton Clojure app.

this repository is a clojure/compojure template add-on for use with the 
Heroku <a href="http://github.com/heroku/kensa">kensa</a> gem

dependencies:

    > gem install kensa
    > gem install foreman

clone it via:

    > kensa create my_addon --template clojure
    > cd my_addon
    > lein deps
    > foreman start

In a new window: 

    > cd my_addon
    > kensa test provision
    > kensa sso 1

And you should be in a Heroku Single Sign On sesion for your brand new addon! 

## Current status: 
- deprovision - working
- provision   - working
- planchange  - working
- GET SSO     - working
- POST SSO    - working

Copyright (C) 2011 Chris Continanza

Distributed under no license.
