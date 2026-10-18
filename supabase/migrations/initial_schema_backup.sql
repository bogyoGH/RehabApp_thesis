


SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;


CREATE EXTENSION IF NOT EXISTS "pg_net" WITH SCHEMA "extensions";






COMMENT ON SCHEMA "public" IS 'standard public schema';



CREATE SCHEMA IF NOT EXISTS "zokni";


ALTER SCHEMA "zokni" OWNER TO "supabase_admin";


CREATE EXTENSION IF NOT EXISTS "pg_stat_statements" WITH SCHEMA "extensions";






CREATE EXTENSION IF NOT EXISTS "pgcrypto" WITH SCHEMA "extensions";






CREATE EXTENSION IF NOT EXISTS "supabase_vault" WITH SCHEMA "vault";






CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA "extensions";






CREATE OR REPLACE FUNCTION "public"."athlete_delete_trg"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    UPDATE zokni.athletes SET is_deleted = true WHERE id = OLD.id;
    RETURN OLD;
END;
$$;


ALTER FUNCTION "public"."athlete_delete_trg"() OWNER TO "supabase_admin";


CREATE OR REPLACE FUNCTION "public"."athlete_insert_trg"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    AS $$
BEGIN
    INSERT INTO zokni.athletes (id, age, gender, height_cm, weight_kg, created_at, is_deleted)
    VALUES (NEW.id, NEW.age, NEW.gender, NEW.height_cm, NEW.weight_kg, COALESCE(NEW.created_at, now()), false);
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."athlete_insert_trg"() OWNER TO "supabase_admin";


CREATE OR REPLACE FUNCTION "public"."athlete_update_trg"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    AS $$
BEGIN
    UPDATE zokni.athletes
    SET age = NEW.age, gender = NEW.gender, height_cm = NEW.height_cm, weight_kg = NEW.weight_kg
    WHERE id = OLD.id;
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."athlete_update_trg"() OWNER TO "supabase_admin";


CREATE OR REPLACE FUNCTION "public"."restore_session"("p_session_id" "text") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    UPDATE zokni.sessions
    SET is_deleted = false
    WHERE session_id = p_session_id AND patient_id = auth.uid()::text;
END;
$$;


ALTER FUNCTION "public"."restore_session"("p_session_id" "text") OWNER TO "supabase_admin";


CREATE OR REPLACE FUNCTION "public"."session_delete_trg"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
BEGIN
    UPDATE zokni.sessions SET is_deleted = true WHERE session_id = OLD.session_id;
    RETURN OLD;
END;
$$;


ALTER FUNCTION "public"."session_delete_trg"() OWNER TO "supabase_admin";


CREATE OR REPLACE FUNCTION "public"."session_insert_trg"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    AS $$
BEGIN
    INSERT INTO zokni.sessions (id, session_id, patient_id, session_data, is_deleted, video_url, analysis_status)
    VALUES (gen_random_uuid(), NEW.session_id, NEW.patient_id, NEW.session_data, false, NEW.video_url, NEW.analysis_status);
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."session_insert_trg"() OWNER TO "supabase_admin";

SET default_tablespace = '';

SET default_table_access_method = "heap";


CREATE TABLE IF NOT EXISTS "zokni"."athletes" (
    "id" "uuid" NOT NULL,
    "age" integer,
    "gender" "text",
    "height_cm" numeric,
    "weight_kg" numeric,
    "created_at" timestamp with time zone DEFAULT "now"(),
    "is_deleted" boolean DEFAULT false
);


ALTER TABLE "zokni"."athletes" OWNER TO "postgres";


CREATE OR REPLACE VIEW "public"."athletes" WITH ("security_invoker"='true') AS
 SELECT "id",
    "age",
    "gender",
    "height_cm",
    "weight_kg",
    "created_at"
   FROM "zokni"."athletes"
  WHERE ("is_deleted" = false);


ALTER VIEW "public"."athletes" OWNER TO "supabase_admin";


CREATE TABLE IF NOT EXISTS "zokni"."sessions" (
    "id" "uuid" NOT NULL,
    "session_id" "text" NOT NULL,
    "patient_id" "text" NOT NULL,
    "is_deleted" boolean,
    "session_data" "jsonb" NOT NULL,
    "video_url" "text",
    "analysis_status" "text" DEFAULT 'pending'::"text"
);


ALTER TABLE "zokni"."sessions" OWNER TO "postgres";


CREATE OR REPLACE VIEW "public"."deleted_sessions" WITH ("security_invoker"='true') AS
 SELECT "id",
    "session_id",
    "patient_id",
    "session_data"
   FROM "zokni"."sessions"
  WHERE ("is_deleted" = true);


ALTER VIEW "public"."deleted_sessions" OWNER TO "supabase_admin";


CREATE TABLE IF NOT EXISTS "zokni"."exercises" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "name" "text" NOT NULL,
    "category" "text",
    "description" "text",
    "instructions" "text",
    "video_url" "text",
    "is_global" boolean DEFAULT false,
    "created_by" "uuid",
    "created_at" timestamp with time zone DEFAULT "now"(),
    "is_deleted" boolean DEFAULT false
);


ALTER TABLE "zokni"."exercises" OWNER TO "supabase_admin";


CREATE OR REPLACE VIEW "public"."exercises" WITH ("security_invoker"='true') AS
 SELECT "id",
    "name",
    "category",
    "description",
    "instructions",
    "video_url",
    "is_global",
    "created_by",
    "created_at",
    "is_deleted"
   FROM "zokni"."exercises"
  WHERE ("is_deleted" = false);


ALTER VIEW "public"."exercises" OWNER TO "supabase_admin";


CREATE OR REPLACE VIEW "public"."sessions" WITH ("security_invoker"='true') AS
 SELECT "id",
    "session_id",
    "patient_id",
    "session_data",
    "video_url",
    "analysis_status"
   FROM "zokni"."sessions"
  WHERE ("is_deleted" = false);


ALTER VIEW "public"."sessions" OWNER TO "supabase_admin";


ALTER TABLE ONLY "zokni"."athletes"
    ADD CONSTRAINT "athletes_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "zokni"."exercises"
    ADD CONSTRAINT "exercises_pkey" PRIMARY KEY ("id");



CREATE OR REPLACE TRIGGER "athlete_delete_trg" INSTEAD OF DELETE ON "public"."athletes" FOR EACH ROW EXECUTE FUNCTION "public"."athlete_delete_trg"();



CREATE OR REPLACE TRIGGER "athlete_insert_trg" INSTEAD OF INSERT ON "public"."athletes" FOR EACH ROW EXECUTE FUNCTION "public"."athlete_insert_trg"();



ALTER TABLE ONLY "zokni"."athletes"
    ADD CONSTRAINT "athletes_id_fkey" FOREIGN KEY ("id") REFERENCES "auth"."users"("id");



ALTER TABLE ONLY "zokni"."exercises"
    ADD CONSTRAINT "exercises_created_by_fkey" FOREIGN KEY ("created_by") REFERENCES "auth"."users"("id");



CREATE POLICY "Gyakorlatok megtekintése" ON "zokni"."exercises" FOR SELECT TO "authenticated" USING ((("is_global" = true) OR ("created_by" = "auth"."uid"())));



CREATE POLICY "Saját gyakorlat létrehozása" ON "zokni"."exercises" FOR INSERT TO "authenticated" WITH CHECK (("created_by" = "auth"."uid"()));



CREATE POLICY "Saját gyakorlat módosítása" ON "zokni"."exercises" FOR UPDATE TO "authenticated" USING (("created_by" = "auth"."uid"()));



CREATE POLICY "Saját gyakorlat törlése" ON "zokni"."exercises" FOR DELETE TO "authenticated" USING (("created_by" = "auth"."uid"()));



CREATE POLICY "Saját sessionök beszúrása" ON "zokni"."sessions" FOR INSERT TO "authenticated" WITH CHECK ((("auth"."uid"())::"text" = "patient_id"));



CREATE POLICY "Saját sessionök megtekintése" ON "zokni"."sessions" FOR SELECT TO "authenticated" USING ((("auth"."uid"())::"text" = "patient_id"));



CREATE POLICY "Saját sessionök módosítása" ON "zokni"."sessions" FOR UPDATE TO "authenticated" USING ((("auth"."uid"())::"text" = "patient_id"));



CREATE POLICY "Sportoló láthatja a saját profilját" ON "zokni"."athletes" FOR SELECT USING (("auth"."uid"() = "id"));



CREATE POLICY "Sportoló létrehozhatja a saját profilját" ON "zokni"."athletes" FOR INSERT WITH CHECK (("auth"."uid"() = "id"));



CREATE POLICY "Sportoló módosíthatja a saját profilját" ON "zokni"."athletes" FOR UPDATE USING (("auth"."uid"() = "id"));



ALTER TABLE "zokni"."athletes" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "zokni"."exercises" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "zokni"."sessions" ENABLE ROW LEVEL SECURITY;




ALTER PUBLICATION "supabase_realtime" OWNER TO "postgres";





GRANT USAGE ON SCHEMA "public" TO "postgres";
GRANT USAGE ON SCHEMA "public" TO "anon";
GRANT USAGE ON SCHEMA "public" TO "authenticated";
GRANT USAGE ON SCHEMA "public" TO "service_role";



GRANT USAGE ON SCHEMA "zokni" TO "anon";
GRANT USAGE ON SCHEMA "zokni" TO "authenticated";






















































































































































GRANT ALL ON FUNCTION "public"."athlete_delete_trg"() TO "postgres";
GRANT ALL ON FUNCTION "public"."athlete_delete_trg"() TO "anon";
GRANT ALL ON FUNCTION "public"."athlete_delete_trg"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."athlete_delete_trg"() TO "service_role";



GRANT ALL ON FUNCTION "public"."athlete_insert_trg"() TO "postgres";
GRANT ALL ON FUNCTION "public"."athlete_insert_trg"() TO "anon";
GRANT ALL ON FUNCTION "public"."athlete_insert_trg"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."athlete_insert_trg"() TO "service_role";



GRANT ALL ON FUNCTION "public"."athlete_update_trg"() TO "postgres";
GRANT ALL ON FUNCTION "public"."athlete_update_trg"() TO "anon";
GRANT ALL ON FUNCTION "public"."athlete_update_trg"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."athlete_update_trg"() TO "service_role";



GRANT ALL ON FUNCTION "public"."restore_session"("p_session_id" "text") TO "postgres";
GRANT ALL ON FUNCTION "public"."restore_session"("p_session_id" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."restore_session"("p_session_id" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."restore_session"("p_session_id" "text") TO "service_role";



GRANT ALL ON FUNCTION "public"."session_delete_trg"() TO "postgres";
GRANT ALL ON FUNCTION "public"."session_delete_trg"() TO "anon";
GRANT ALL ON FUNCTION "public"."session_delete_trg"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."session_delete_trg"() TO "service_role";



GRANT ALL ON FUNCTION "public"."session_insert_trg"() TO "postgres";
GRANT ALL ON FUNCTION "public"."session_insert_trg"() TO "anon";
GRANT ALL ON FUNCTION "public"."session_insert_trg"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."session_insert_trg"() TO "service_role";


















GRANT ALL ON TABLE "zokni"."athletes" TO "anon";
GRANT ALL ON TABLE "zokni"."athletes" TO "authenticated";
GRANT ALL ON TABLE "zokni"."athletes" TO "service_role";



GRANT ALL ON TABLE "public"."athletes" TO "postgres";
GRANT ALL ON TABLE "public"."athletes" TO "anon";
GRANT ALL ON TABLE "public"."athletes" TO "authenticated";
GRANT ALL ON TABLE "public"."athletes" TO "service_role";



GRANT SELECT,INSERT ON TABLE "zokni"."sessions" TO "authenticated";
GRANT SELECT,INSERT ON TABLE "zokni"."sessions" TO "anon";



GRANT ALL ON TABLE "public"."deleted_sessions" TO "postgres";
GRANT ALL ON TABLE "public"."deleted_sessions" TO "anon";
GRANT ALL ON TABLE "public"."deleted_sessions" TO "authenticated";
GRANT ALL ON TABLE "public"."deleted_sessions" TO "service_role";



GRANT ALL ON TABLE "zokni"."exercises" TO "authenticated";



GRANT ALL ON TABLE "public"."exercises" TO "postgres";
GRANT ALL ON TABLE "public"."exercises" TO "anon";
GRANT ALL ON TABLE "public"."exercises" TO "authenticated";
GRANT ALL ON TABLE "public"."exercises" TO "service_role";



GRANT ALL ON TABLE "public"."sessions" TO "postgres";
GRANT ALL ON TABLE "public"."sessions" TO "anon";
GRANT ALL ON TABLE "public"."sessions" TO "authenticated";
GRANT ALL ON TABLE "public"."sessions" TO "service_role";









ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES TO "postgres";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES TO "service_role";






ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS TO "postgres";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS TO "service_role";






ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES TO "postgres";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES TO "service_role";































