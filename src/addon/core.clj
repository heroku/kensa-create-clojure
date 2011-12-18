(ns addon.core
  (:use [compojure.core]
        [ring.adapter.jetty]
        [ring.middleware.basic-auth]
        [ring.middleware.cookies]
        [hiccup.core]
        [ring.util.response])
  (:require [compojure.route :as route]
            [clj-json.core :as json]
            [clj-http.client :as http]
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

(defn sso-page [header id email] 
  (html  
    [:body 
      header 
      [:div 
        "Hello!"
        [:div "You are using resource: " id] 
        [:div "You are on plan: " (@resources id)]
        [:div "You logged in using: " email]]]))

(defn sso [id {:keys [timestamp token nav-data email]}]
  (try 
    (let [expected-token   (sha1 (str id ":" (env "SSO_SALT") ":" timestamp))
          timestamp-check  (> (Integer/parseInt timestamp)
                             (- (int (/ (System/currentTimeMillis) 1000)) (* 5 60)))
          header           (get (http/get "http://nav.heroku.com/v1/providers/header") :body)]
      (if (and (= expected-token token) timestamp-check)
        (-> (response (sso-page header id email))
            (status 200) (content-type "text/html") 
            (assoc :cookies {:heroku-nav-data nav-data}))
        (-> (response "Access denied!") (status 403))))
  (catch NumberFormatException e (-> (response "Access denied!") (status 403)))))

(defn provision []
  (let [id (str (java.util.UUID/randomUUID))]
    (dosync 
      (alter resources assoc id "test"))
    (-> (response (json/generate-string 
                    {"id" id "config" {"MYADDON_URL" (str "http://myaddon.com/" id)}}))
                  (status 201))))

(defn deprovision [id]
  (dosync 
    (if (@resources id) 
      (alter resources dissoc id)
      (-> (response "Not found") 
          (status 404)))))

(defn plan-change [id body]
  (dosync 
    (if (@resources id) 
      (alter resources assoc id (:plan body))
      (-> (response "Not found") 
          (status 404)))))

(defroutes heroku-routes
  (DELETE "/heroku/resources/:id" [id] (deprovision id))
  (PUT    "/heroku/resources/:id" {body :body {id :id} :params} 
    (plan-change id (json/parse-string (slurp body))))
  (POST   "/heroku/resources" [] (provision)))

(defroutes user-routes
  (GET    "/" [] "Hello, world")
  (POST   "/sso/login" {params :params} (sso (:id params) params)) 
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
