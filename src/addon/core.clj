(ns addon.core
  (:use [compojure.core]
        [ring.adapter.jetty]
        [ring.middleware.basic-auth]
        [clj-json.core])
  (:require [compojure.route :as route]))

(defn env [k]
  (System/getenv k))

(defn get-hash [type data]
  (.digest (java.security.MessageDigest/getInstance type) (.getBytes data)))

(defn sha1-hash [data]
 (get-hash "sha1" data))

(defn get-hash-str [data-bytes]
  (apply str
    (map
    #(.substring
    (Integer/toString
    (+ (bit-and % 0xff) 0x100) 16) 1)
    data-bytes)))

(defn sso [id params]
  (let [expected-token (get-hash-str (sha1-hash (str id ":" (env "SSO_SALT") ":" (get params "timestamp"))))
        actual-token (get params "token")]
    (prn expected-token)
    (prn actual-token)
  (if (= expected-token actual-token)
    "You're in!"
    {:status 403 :headers {} :body ""})))

(defn provision []
  (generate-string {"id" 1 "config" {"MYADDON_URL" "http://google.com"}}))

(defroutes heroku-routes
  (DELETE "/heroku/resources/:id" [id] "ok")
  (PUT    "/heroku/resources/:id" [id] "ok")
  (POST   "/heroku/resources" [] (provision)))

(defroutes user-routes
  (GET    "/" [] "Hello, world")
  (GET    "/heroku/resources/:id" [id & params] (sso id params)))

(defn authenticate [username password]
  (and (= username (env "HEROKU_USERNAME"))
       (= password (env "HEROKU_PASSWORD"))))

(defroutes main-routes
  user-routes
  (wrap-basic-auth heroku-routes authenticate))

(defn -main []
  (let [port (Integer/parseInt (env "PORT"))]
    (run-jetty main-routes {:port port})))
