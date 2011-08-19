(ns addon.core
  (:use [compojure.core]
        [ring.adapter.jetty]
        [ring.middleware.basic-auth]
        [ring.util.response]
        [clj-json.core])
  (:require [compojure.route :as route]
            [compojure.handler :as handler]))

(defn env [k]
  (System/getenv k))

(defn sha1 [plaintext-str]
  (let [plaintext-bytes (.getBytes plaintext-str)
        digest-bytes (.digest (java.security.MessageDigest/getInstance "sha1") plaintext-bytes)]
    (apply str
      (map
        #(.substring (Integer/toString (+ (bit-and % 0xff) 0x100) 16) 1)
        digest-bytes))))

(defn sso [id {:keys [timestamp token]}]
  (let [expected-token (sha1 (str id ":" (env "SSO_SALT") ":" timestamp))]
    (if (or (not= expected-token token)
            (< (Integer/parseInt timestamp)
               (- (int (/ (System/currentTimeMillis) 1000)) (* 5 60))))
      (-> (response "Access denied!") (status 403))
      (-> (response "You're in!") (status 200)))))

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

(def app
  (handler/site
    (routes
      user-routes
      (wrap-basic-auth heroku-routes authenticate))))

(defn -main []
  (let [port (Integer/parseInt (env "PORT"))]
    (run-jetty app {:port port})))
