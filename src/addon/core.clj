(ns addon.core
  (:use [compojure.core]
        [ring.adapter.jetty]
        [ring.middleware.basic-auth]
        [clj-json.core])
  (:require [compojure.route :as route]))

(defn get-hash [type data]
(.digest (java.security.MessageDigest/getInstance type) (.getBytes data) ))

(defn sha1-hash [data]
 (get-hash "sha1" data))

(defn get-hash-str [data-bytes]
  (apply str
(map
#(.substring
(Integer/toString
(+ (bit-and % 0xff) 0x100) 16) 1)
data-bytes)
))

(defn sso [id params] 
  (if (.equals (get-hash-str (sha1-hash (str id ":" (System/getenv "SSO_SALT") ":" (get params "timestamp"))))
               (get params "token"))
      "You're in!"
      {:status 403 :headers {} :body ""}))


(defroutes heroku-routes
  (GET "/" [] "Hello, world")
  (GET    "/heroku/resources/:id" [id & params] (sso id params))
  (DELETE "/heroku/resources/:id" [id] "ok")
  (PUT    "/heroku/resources/:id" [id] "ok")
  (POST   "/heroku/resources" [] 
    (generate-string {:id 1 :config {:MYADDON_URL "http://google.com"}})))

(defn authenticate [username password]
  (and (= username (System/getenv "HEROKU_USERNAME"))
       (= password (System/getenv "HEROKU_PASSWORD"))))

(defroutes main-routes
  (wrap-basic-auth heroku-routes authenticate))

(defn -main []
  (let [port (Integer/parseInt (System/getenv "PORT"))]
    (run-jetty main-routes {:port port})))
