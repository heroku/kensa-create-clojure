(ns addon.core
  (:use [compojure.core]
        [ring.adapter.jetty]
        [ring.middleware.basic-auth]
        [ring.middleware.cookies]
        [ring.util.response]
        [clj-json.core])
  (:require [compojure.route :as route]
            [clojure.contrib.http.agent :as http]
            [compojure.handler :as handler]))

(def resources (ref {}))

(defn env [k]
  (System/getenv k))

(defn authenticate [username password]
  (and (= username (env "HEROKU_USERNAME"))
       (= password (env "HEROKU_PASSWORD"))))

(defn wrap-logging [f] 
  (fn [{:keys [query-params headers body] :as req} & args] 
    (println "PARAMS"  (pr-str query-params))
    (println "HEADERS" (pr-str headers))
    (println "BODY"    (slurp body ))
    (apply f req args)))

(defn sha1 [plaintext-str]
  (let [plaintext-bytes (.getBytes plaintext-str)
        digest-bytes (.digest (java.security.MessageDigest/getInstance "sha1") plaintext-bytes)]
    (apply str
      (map
        #(.substring (Integer/toString (+ (bit-and % 0xff) 0x100) 16) 1)
        digest-bytes))))

(defn sso [id {:keys [timestamp token nav-data]}]
  (try 
    (let [expected-token   (sha1 (str id ":" (env "SSO_SALT") ":" timestamp))
          timestamp-check  (> (Integer/parseInt timestamp)
                             (- (int (/ (System/currentTimeMillis) 1000)) (* 5 60)))
          header           (http/string (http/http-agent "http://nav.heroku.com/v1/providers/header"))]
      (if (and (= expected-token token) timestamp-check)
        (-> (response (str "<html><body>" header "<p>Hello, world!</p></body></html>"))
            (status 200) (content-type "text/html") 
            (assoc :cookies {:heroku-nav-data nav-data}))
        (-> (response "Access denied!") (status 403))))
  (catch NumberFormatException e (-> (response "Access denied!") (status 403)))))

(defn provision []
  (let [id (str (java.util.UUID/randomUUID))]
    (dosync 
      (alter resources assoc id "test"))
    (-> (response (generate-string 
                    {"id" id "config" {"MYADDON_URL" (str "http://myaddon.com/" id)}}))
                  (status 201))))

(defn deprovision [id]
  (dosync 
    (if (@resources id) 
      (alter resources dissoc id)
      (-> (response "Not found") 
          (status 404)))))

(defroutes heroku-routes
  (DELETE "/heroku/resources/:id" [id] (deprovision id))
  (PUT    "/heroku/resources/:id" [id] "ok")
  (POST   "/heroku/resources" [] (provision)))

(defroutes user-routes
  (GET    "/" [] "Hello, world")
  (GET    "/heroku/resources/:id" [id & params] (sso id params)))

(def app
  (handler/site
    (wrap-logging 
      (routes
        (wrap-cookies user-routes)
        (wrap-basic-auth heroku-routes authenticate)))))

(defn -main []
  (let [port (Integer/parseInt (env "PORT"))]
    (run-jetty app {:port port})))
