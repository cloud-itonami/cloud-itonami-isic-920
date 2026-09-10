(ns gamblingfacilityops.store
  "In-memory facility/resource store for gambling facility coordination.
   DEMO ONLY: production requires persistent backing (database, EDN ledger, etc.)

   Stores: facilities (with :registered?/:verified? flags), referral resources, staff.")

(defn make-store
  "Create an in-memory MemStore with demo facilities and referral resources."
  []
  (atom {:facilities
         {:casino-downtown
          {:id :casino-downtown
           :name "Downtown Casino & Resort"
           :registered? true
           :verified? true
           :location "Main St, Downtown"}
          :racebook-metro
          {:id :racebook-metro
           :name "Metro Racebook"
           :registered? true
           :verified? true
           :location "Business District"}}

         :referral-resources
         {:problem-gambling-hotline
          {:id :problem-gambling-hotline
           :name "National Problem Gambling Helpline"
           :phone "1-800-522-4700"
           :available? true}
          :self-exclusion-program
          {:id :self-exclusion-program
           :name "State Self-Exclusion Program"
           :url "https://www.gamcare.org.uk"
           :available? true}
          :counseling-service
          {:id :counseling-service
           :name "Community Counseling Center"
           :phone "1-800-123-4567"
           :available? true}}

         :staff
         {:alice-shift-mgr
          {:id :alice-shift-mgr
           :name "Alice"
           :role "Shift Manager"
           :facility :casino-downtown}
          :bob-housekeeping
          {:id :bob-housekeeping
           :name "Bob"
           :role "Housekeeping"
           :facility :racebook-metro}}}))

(defn get-facility
  "Retrieve a facility by ID. Returns nil if not found."
  [store facility-id]
  (get (:facilities @store) facility-id))

(defn facility-verified?
  "Check if facility exists and is registered/verified."
  [store facility-id]
  (boolean (when-let [fac (get-facility store facility-id)]
             (and (:registered? fac) (:verified? fac)))))

(defn get-referral-resource
  "Retrieve a referral resource by ID."
  [store resource-id]
  (get (:referral-resources @store) resource-id))

(defn list-facilities
  "List all facilities."
  [store]
  (vals (:facilities @store)))

(defn list-referral-resources
  "List all available referral resources."
  [store]
  (filter :available? (vals (:referral-resources @store))))
