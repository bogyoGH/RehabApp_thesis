--
-- PostgreSQL database dump
--


-- Dumped from database version 17.6
-- Dumped by pg_dump version 17.6

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: public; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA IF NOT EXISTS public;


--
-- Name: SCHEMA public; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON SCHEMA public IS 'standard public schema';


--
-- Name: zokni; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA IF NOT EXISTS zokni;



-- Kiterjesztesek a korabbi initial_schema.sql fajlbol
CREATE EXTENSION IF NOT EXISTS "pg_net" WITH SCHEMA "extensions";
CREATE EXTENSION IF NOT EXISTS "pg_stat_statements" WITH SCHEMA "extensions";
CREATE EXTENSION IF NOT EXISTS "pgcrypto" WITH SCHEMA "extensions";
CREATE EXTENSION IF NOT EXISTS "supabase_vault" WITH SCHEMA "vault";
CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA "extensions";

--
-- Name: athlete_delete_trg(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.athlete_delete_trg() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    AS $$
BEGIN
    UPDATE zokni.athletes SET is_deleted = true WHERE id = OLD.id;
    RETURN OLD;
END;
$$;


--
-- Name: athlete_insert_trg(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.athlete_insert_trg() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    INSERT INTO zokni.athletes (id, age, gender, height_cm, weight_kg, created_at, is_deleted)
    VALUES (NEW.id, NEW.age, NEW.gender, NEW.height_cm, NEW.weight_kg, COALESCE(NEW.created_at, now()), false);
    RETURN NEW;
END;
$$;


--
-- Name: athlete_update_trg(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.athlete_update_trg() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    UPDATE zokni.athletes
    SET age = NEW.age, gender = NEW.gender, height_cm = NEW.height_cm, weight_kg = NEW.weight_kg
    WHERE id = OLD.id;
    RETURN NEW;
END;
$$;


--
-- Name: evaluate_biomechanics(text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.evaluate_biomechanics(p_session_id text DEFAULT NULL::text) RETURNS TABLE(session_id text, osszpontszam integer, mean_velocity numeric, sway_path numeric, sway_area numeric, torzs_ingadozas_sd numeric, stabilitas_pont numeric, alatamasztas_pont numeric, szimmetria_pont numeric, terhelesi_irany text, ertekeles text)
    LANGUAGE plpgsql
    AS $$
BEGIN
    RETURN QUERY
        WITH
            -- 1. Célzott kulcspontok lekérése és 0.7-es konfidenciaszűrés
            raw_kp AS (
                SELECT
                    ek.session_id::TEXT AS sid,
                    ek.frame_index,
                    ek.keypoint_name,
                    CASE WHEN ek.confidence >= 0.7 THEN ek.x::DOUBLE PRECISION ELSE NULL END AS x,
                    CASE WHEN ek.confidence >= 0.7 THEN ek.y::DOUBLE PRECISION ELSE NULL END AS y
                FROM zokni.extracted_keypoints ek
                WHERE (p_session_id IS NULL OR ek.session_id = p_session_id)
                  AND ek.keypoint_name IN (
                                           'hip_center', 'left_hip', 'right_hip',
                                           'left_shoulder', 'right_shoulder',
                                           'left_elbow', 'right_elbow',
                                           'left_wrist', 'right_wrist',
                                           'left_knee', 'right_knee',
                                           'left_ankle', 'right_ankle'
                    )
            ),
            -- 2. Mozgó átlag alapú kiugróérték-szűrés (JUMP_THRESHOLD = 25.0 px)
            outlier_filtered AS (
                SELECT
                    sid, frame_index, keypoint_name,
                    CASE
                        WHEN ABS(x - AVG(x) OVER w) > 25.0 THEN NULL
                        ELSE x
                        END AS x_clean,
                    CASE
                        WHEN ABS(y - AVG(y) OVER w) > 25.0 THEN NULL
                        ELSE y
                        END AS y_clean
                FROM raw_kp
                WINDOW w AS (PARTITION BY sid, keypoint_name ORDER BY frame_index ROWS BETWEEN 2 PRECEDING AND 2 FOLLOWING)
            ),
            -- 3. Nulla fázistolású (Zero-Phase) 5-pontos súlyozott aluláteresztő szűrés (Winter, 2009)
            smoothed_kp AS (
                SELECT
                    sid, frame_index, keypoint_name,
                    (
                        1.0 * COALESCE(LAG(x_clean, 2) OVER w, LAG(x_clean, 1) OVER w, x_clean) +
                        4.0 * COALESCE(LAG(x_clean, 1) OVER w, x_clean) +
                        6.0 * COALESCE(x_clean, LAG(x_clean, 1) OVER w, LEAD(x_clean, 1) OVER w) +
                        4.0 * COALESCE(LEAD(x_clean, 1) OVER w, x_clean) +
                        1.0 * COALESCE(LEAD(x_clean, 2) OVER w, LEAD(x_clean, 1) OVER w, x_clean)
                        ) / 16.0 AS x_s,
                    (
                        1.0 * COALESCE(LAG(y_clean, 2) OVER w, LAG(y_clean, 1) OVER w, y_clean) +
                        4.0 * COALESCE(LAG(y_clean, 1) OVER w, y_clean) +
                        6.0 * COALESCE(y_clean, LAG(y_clean, 1) OVER w, LEAD(y_clean, 1) OVER w) +
                        4.0 * COALESCE(LEAD(y_clean, 1) OVER w, y_clean) +
                        1.0 * COALESCE(LEAD(y_clean, 2) OVER w, LEAD(y_clean, 1) OVER w, y_clean)
                        ) / 16.0 AS y_s
                FROM outlier_filtered
                WINDOW w AS (PARTITION BY sid, keypoint_name ORDER BY frame_index)
            ),
            -- 4. Pivotálás (Képkockánként 1 sor az összes testrésszel)
            pivoted AS (
                SELECT
                    sid, frame_index,
                    MAX(x_s) FILTER (WHERE keypoint_name = 'hip_center') AS x_hip_c,
                    MAX(y_s) FILTER (WHERE keypoint_name = 'hip_center') AS y_hip_c,
                    MAX(x_s) FILTER (WHERE keypoint_name = 'left_hip') AS x_l_hip,
                    MAX(y_s) FILTER (WHERE keypoint_name = 'left_hip') AS y_l_hip,
                    MAX(x_s) FILTER (WHERE keypoint_name = 'right_hip') AS x_r_hip,
                    MAX(y_s) FILTER (WHERE keypoint_name = 'right_hip') AS y_r_hip,
                    MAX(x_s) FILTER (WHERE keypoint_name = 'left_shoulder') AS x_l_sh,
                    MAX(y_s) FILTER (WHERE keypoint_name = 'left_shoulder') AS y_l_sh,
                    MAX(x_s) FILTER (WHERE keypoint_name = 'right_shoulder') AS x_r_sh,
                    MAX(y_s) FILTER (WHERE keypoint_name = 'right_shoulder') AS y_r_sh,
                    MAX(x_s) FILTER (WHERE keypoint_name = 'left_elbow') AS x_l_elb,
                    MAX(y_s) FILTER (WHERE keypoint_name = 'left_elbow') AS y_l_elb,
                    MAX(x_s) FILTER (WHERE keypoint_name = 'right_elbow') AS x_r_elb,
                    MAX(y_s) FILTER (WHERE keypoint_name = 'right_elbow') AS y_r_elb,
                    MAX(x_s) FILTER (WHERE keypoint_name = 'left_wrist') AS x_l_wri,
                    MAX(y_s) FILTER (WHERE keypoint_name = 'left_wrist') AS y_l_wri,
                    MAX(x_s) FILTER (WHERE keypoint_name = 'right_wrist') AS x_r_wri,
                    MAX(y_s) FILTER (WHERE keypoint_name = 'right_wrist') AS y_r_wri,
                    MAX(x_s) FILTER (WHERE keypoint_name = 'left_knee') AS x_l_knee,
                    MAX(y_s) FILTER (WHERE keypoint_name = 'left_knee') AS y_l_knee,
                    MAX(x_s) FILTER (WHERE keypoint_name = 'right_knee') AS x_r_knee,
                    MAX(y_s) FILTER (WHERE keypoint_name = 'right_knee') AS y_r_knee,
                    MAX(x_s) FILTER (WHERE keypoint_name = 'left_ankle') AS x_l_ank,
                    MAX(y_s) FILTER (WHERE keypoint_name = 'left_ankle') AS y_l_ank,
                    MAX(x_s) FILTER (WHERE keypoint_name = 'right_ankle') AS x_r_ank,
                    MAX(y_s) FILTER (WHERE keypoint_name = 'right_ankle') AS y_r_ank
                FROM smoothed_kp
                GROUP BY sid, frame_index
            ),
            -- 5. de Leva (1996) szegmentális CoM és BoS számítás képkockánként
            com_calc AS (
                SELECT
                    p.*,
                    (x_l_sh + x_r_sh) / 2.0 AS x_sh_mid,
                    (y_l_sh + y_r_sh) / 2.0 AS y_sh_mid,
                    COALESCE((x_l_hip + x_r_hip) / 2.0, x_hip_c) AS x_hip_mid,
                    COALESCE((y_l_hip + y_r_hip) / 2.0, y_hip_c) AS y_hip_mid,
                    (x_l_ank + x_r_ank) / 2.0 AS x_ank_mid,
                    (y_l_ank + y_r_ank) / 2.0 AS y_ank_mid,
                    LEAST(x_l_ank, x_r_ank) AS bos_left,
                    GREATEST(x_l_ank, x_r_ank) AS bos_right,
                    ABS(x_l_ank - x_r_ank) AS bos_width
                FROM pivoted p
                WHERE x_l_ank IS NOT NULL AND x_r_ank IS NOT NULL
                  AND y_l_sh IS NOT NULL AND y_r_sh IS NOT NULL
                  AND (x_hip_c IS NOT NULL OR (x_l_hip IS NOT NULL AND x_r_hip IS NOT NULL))
            ),
            com_final AS (
                SELECT
                    c.*,
                    -- Teljes 9-szegmenses de Leva (1996), különben 2-szegmenses de Leva Fallback
                    CASE
                        WHEN x_l_knee IS NOT NULL AND x_r_knee IS NOT NULL AND x_l_elb IS NOT NULL AND x_r_elb IS NOT NULL THEN
                            0.5034 * (x_sh_mid + 0.4486 * (x_hip_mid - x_sh_mid)) +
                            0.0263 * (x_l_sh + 0.5772 * (x_l_elb - x_l_sh)) +
                            0.0263 * (x_r_sh + 0.5772 * (x_r_elb - x_r_sh)) +
                            0.0207 * (x_l_elb + 0.4574 * (COALESCE(x_l_wri, x_l_elb) - x_l_elb)) +
                            0.0207 * (x_r_elb + 0.4574 * (COALESCE(x_r_wri, x_r_elb) - x_r_elb)) +
                            0.1447 * (COALESCE(x_l_hip, x_hip_mid) + 0.4095 * (x_l_knee - COALESCE(x_l_hip, x_hip_mid))) +
                            0.1447 * (COALESCE(x_r_hip, x_hip_mid) + 0.4095 * (x_r_knee - COALESCE(x_r_hip, x_hip_mid))) +
                            0.0566 * (x_l_knee + 0.4459 * (x_l_ank - x_l_knee)) +
                            0.0566 * (x_r_knee + 0.4459 * (x_r_ank - x_r_knee))
                        ELSE
                            0.5034 * (x_sh_mid + 0.4486 * (x_hip_mid - x_sh_mid)) +
                            0.4966 * (x_hip_mid + 0.4200 * (x_ank_mid - x_hip_mid))
                        END AS x_com,
                    CASE
                        WHEN y_l_knee IS NOT NULL AND y_r_knee IS NOT NULL AND y_l_elb IS NOT NULL AND y_r_elb IS NOT NULL THEN
                            0.5034 * (y_sh_mid + 0.4486 * (y_hip_mid - y_sh_mid)) +
                            0.0263 * (y_l_sh + 0.5772 * (y_l_elb - y_l_sh)) +
                            0.0263 * (y_r_sh + 0.5772 * (y_r_elb - y_r_sh)) +
                            0.0207 * (y_l_elb + 0.4574 * (COALESCE(y_l_wri, y_l_elb) - y_l_elb)) +
                            0.0207 * (y_r_elb + 0.4574 * (COALESCE(y_r_wri, y_r_elb) - y_r_elb)) +
                            0.1447 * (COALESCE(y_l_hip, y_hip_mid) + 0.4095 * (y_l_knee - COALESCE(y_l_hip, y_hip_mid))) +
                            0.1447 * (COALESCE(y_r_hip, y_hip_mid) + 0.4095 * (y_r_knee - COALESCE(y_r_hip, y_hip_mid))) +
                            0.0566 * (y_l_knee + 0.4459 * (y_l_ank - y_l_knee)) +
                            0.0566 * (y_r_knee + 0.4459 * (y_r_ank - y_r_knee))
                        ELSE
                            0.5034 * (y_sh_mid + 0.4486 * (y_hip_mid - y_sh_mid)) +
                            0.4966 * (y_hip_mid + 0.4200 * (y_ank_mid - y_hip_mid))
                        END AS y_com
                FROM com_calc c
            ),
            -- 6. Anatómiai skálázó faktor
            scales AS (
                SELECT
                    sid,
                    PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY ABS(y_ank_mid - y_sh_mid)) + 1e-6 AS ref_scale
                FROM com_final
                GROUP BY sid
            ),
            -- 7. Normalizálás és differenciák
            normalized AS (
                SELECT
                    f.sid,
                    f.frame_index,
                    f.x_com / s.ref_scale AS com_x_norm,
                    f.y_com / s.ref_scale AS com_y_norm,
                    (f.x_com / s.ref_scale) - LAG(f.x_com / s.ref_scale) OVER w AS dx,
                    (f.y_com / s.ref_scale) - LAG(f.y_com / s.ref_scale) OVER w AS dy,
                    f.bos_width / s.ref_scale AS bos_w_norm,
                    (f.bos_width / s.ref_scale) - LAG(f.bos_width / s.ref_scale) OVER w AS d_bos,
                    (f.x_com - f.bos_left) / (f.bos_width + 1e-6) AS norm_com_pos,
                    DEGREES(ATAN2(f.x_sh_mid - f.x_hip_mid, ABS(f.y_hip_mid - f.y_sh_mid) + 1e-6)) AS trunk_angle_deg
                FROM com_final f
                         JOIN scales s ON f.sid = s.sid
                WINDOW w AS (PARTITION BY f.sid ORDER BY f.frame_index)
            ),
            -- 8. Statisztikai aggregáció
            metrics AS (
                SELECT
                    sid,
                    SUM(SQRT(dx*dx + dy*dy)) AS sway_path_len,
                    AVG(SQRT(dx*dx + dy*dy)) * 30.0 AS mean_vel,
                    STDDEV_SAMP(trunk_angle_deg) AS hip_sd,
                    AVG(ABS(d_bos)) * 30.0 AS bos_vol,
                    PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY norm_com_pos) AS med_com_pos,
                    VAR_SAMP(com_x_norm) AS var_x,
                    VAR_SAMP(com_y_norm) AS var_y,
                    COVAR_SAMP(com_x_norm, com_y_norm) AS cov_xy
                FROM normalized
                GROUP BY sid
            ),
            -- 9. Végső pontszámok
            scored AS (
                SELECT
                    m.sid,
                    m.mean_vel,
                    m.sway_path_len,
                    PI() * 5.991 * SQRT(GREATEST(0.0, m.var_x * m.var_y - m.cov_xy * m.cov_xy)) AS area_ellipse,
                    m.hip_sd,
                    m.med_com_pos,
                    LEAST(100.0, GREATEST(0.0, 100.0 * (1.0 - (m.mean_vel / 0.10)))) AS s_score,
                    LEAST(100.0, GREATEST(0.0, 100.0 * (1.0 - (m.bos_vol / 0.08)))) AS b_score,
                    LEAST(100.0, GREATEST(0.0, 100.0 * (1.0 - (ABS(m.med_com_pos - 0.5) / 0.35)))) AS sym_score
                FROM metrics m
            )
        SELECT
            s.sid AS session_id,
            CAST(0.40 * s.s_score + 0.35 * s.b_score + 0.25 * s.sym_score AS INT) AS osszpontszam,
            ROUND(s.mean_vel::NUMERIC, 4) AS mean_velocity,
            ROUND(s.sway_path_len::NUMERIC, 3) AS sway_path,
            ROUND(s.area_ellipse::NUMERIC, 5) AS sway_area,
            ROUND(s.hip_sd::NUMERIC, 2) AS torzs_ingadozas_sd,
            ROUND(s.s_score::NUMERIC, 1) AS stabilitas_pont,
            ROUND(s.b_score::NUMERIC, 1) AS alatamasztas_pont,
            ROUND(s.sym_score::NUMERIC, 1) AS szimmetria_pont,
            CASE
                WHEN s.med_com_pos < 0.45 THEN 'Balra'
                WHEN s.med_com_pos > 0.55 THEN 'Jobbra'
                ELSE 'Középen'
                END AS terhelesi_irany,
            CASE
                WHEN CAST(0.40 * s.s_score + 0.35 * s.b_score + 0.25 * s.sym_score AS INT) >= 85 THEN 'Kiváló'
                WHEN CAST(0.40 * s.s_score + 0.35 * s.b_score + 0.25 * s.sym_score AS INT) >= 65 THEN 'Jó'
                ELSE 'Instabil'
                END AS ertekeles
        FROM scored s
        ORDER BY osszpontszam DESC;
END;
$$;


--
-- Name: restore_session(text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.restore_session(p_session_id text) RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER
    AS $$
BEGIN
    UPDATE zokni.sessions
    SET is_deleted = false
    WHERE session_id = p_session_id AND patient_id = auth.uid()::text;
END;
$$;


--
-- Name: session_delete_trg(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.session_delete_trg() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    AS $$
BEGIN
    UPDATE zokni.sessions SET is_deleted = true WHERE session_id = OLD.session_id;
    RETURN OLD;
END;
$$;


--
-- Name: session_insert_trg(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.session_insert_trg() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    INSERT INTO zokni.sessions (id, session_id, patient_id, session_data, is_deleted, video_url, analysis_status)
    VALUES (gen_random_uuid(), NEW.session_id, NEW.patient_id, NEW.session_data, false, NEW.video_url, NEW.analysis_status);
    RETURN NEW;
END;
$$;


--
-- Name: evaluate_and_store_biomechanics(text); Type: FUNCTION; Schema: zokni; Owner: -
--

CREATE FUNCTION zokni.evaluate_and_store_biomechanics(p_session_id text) RETURNS void
    LANGUAGE plpgsql
    AS $$
BEGIN
    INSERT INTO zokni.biomechanics_results (
        session_id, osszpontszam, mean_velocity, sway_path, sway_area,
        torzs_ingadozas_sd, stabilitas_pont, alatamasztas_pont, szimmetria_pont,
        terhelesi_irany, ertekeles, kiszamitva
    )
    WITH
        -- 1. Célzott kulcspontok lekérése és 0.7-es konfidenciaszűrés
        raw_kp AS (
            SELECT
                ek.session_id::TEXT AS sid, ek.frame_index, ek.keypoint_name,
                CASE WHEN ek.confidence >= 0.7 THEN ek.x::DOUBLE PRECISION ELSE NULL END AS x,
                CASE WHEN ek.confidence >= 0.7 THEN ek.y::DOUBLE PRECISION ELSE NULL END AS y
            FROM zokni.extracted_keypoints ek
            WHERE ek.session_id = p_session_id
              AND ek.keypoint_name IN (
                                       'hip_center', 'left_hip', 'right_hip', 'left_shoulder', 'right_shoulder',
                                       'left_elbow', 'right_elbow', 'left_wrist', 'right_wrist',
                                       'left_knee', 'right_knee', 'left_ankle', 'right_ankle'
                )
        ),
        -- 2. Mozgó átlag alapú kiugróérték-szűrés (JUMP_THRESHOLD = 25.0 px)
        outlier_filtered AS (
            SELECT sid, frame_index, keypoint_name,
                   CASE WHEN ABS(x - AVG(x) OVER w) > 25.0 THEN NULL ELSE x END AS x_clean,
                   CASE WHEN ABS(y - AVG(y) OVER w) > 25.0 THEN NULL ELSE y END AS y_clean
            FROM raw_kp
            WINDOW w AS (PARTITION BY sid, keypoint_name ORDER BY frame_index ROWS BETWEEN 2 PRECEDING AND 2 FOLLOWING)
        ),
        -- 3. Nulla fázistolású 5-pontos súlyozott aluláteresztő szűrés
        smoothed_kp AS (
            SELECT sid, frame_index, keypoint_name,
                   (
                       1.0 * COALESCE(LAG(x_clean, 2) OVER w, LAG(x_clean, 1) OVER w, x_clean) +
                       4.0 * COALESCE(LAG(x_clean, 1) OVER w, x_clean) +
                       6.0 * COALESCE(x_clean, LAG(x_clean, 1) OVER w, LEAD(x_clean, 1) OVER w) +
                       4.0 * COALESCE(LEAD(x_clean, 1) OVER w, x_clean) +
                       1.0 * COALESCE(LEAD(x_clean, 2) OVER w, LEAD(x_clean, 1) OVER w, x_clean)
                       ) / 16.0 AS x_s,
                   (
                       1.0 * COALESCE(LAG(y_clean, 2) OVER w, LAG(y_clean, 1) OVER w, y_clean) +
                       4.0 * COALESCE(LAG(y_clean, 1) OVER w, y_clean) +
                       6.0 * COALESCE(y_clean, LAG(y_clean, 1) OVER w, LEAD(y_clean, 1) OVER w) +
                       4.0 * COALESCE(LEAD(y_clean, 1) OVER w, y_clean) +
                       1.0 * COALESCE(LEAD(y_clean, 2) OVER w, LEAD(y_clean, 1) OVER w, y_clean)
                       ) / 16.0 AS y_s
            FROM outlier_filtered
            WINDOW w AS (PARTITION BY sid, keypoint_name ORDER BY frame_index)
        ),
        -- 4. Pivotálás
        pivoted AS (
            SELECT sid, frame_index,
                   MAX(x_s) FILTER (WHERE keypoint_name = 'hip_center') AS x_hip_c,
                   MAX(y_s) FILTER (WHERE keypoint_name = 'hip_center') AS y_hip_c,
                   MAX(x_s) FILTER (WHERE keypoint_name = 'left_hip') AS x_l_hip,
                   MAX(y_s) FILTER (WHERE keypoint_name = 'left_hip') AS y_l_hip,
                   MAX(x_s) FILTER (WHERE keypoint_name = 'right_hip') AS x_r_hip,
                   MAX(y_s) FILTER (WHERE keypoint_name = 'right_hip') AS y_r_hip,
                   MAX(x_s) FILTER (WHERE keypoint_name = 'left_shoulder') AS x_l_sh,
                   MAX(y_s) FILTER (WHERE keypoint_name = 'left_shoulder') AS y_l_sh,
                   MAX(x_s) FILTER (WHERE keypoint_name = 'right_shoulder') AS x_r_sh,
                   MAX(y_s) FILTER (WHERE keypoint_name = 'right_shoulder') AS y_r_sh,
                   MAX(x_s) FILTER (WHERE keypoint_name = 'left_elbow') AS x_l_elb,
                   MAX(y_s) FILTER (WHERE keypoint_name = 'left_elbow') AS y_l_elb,
                   MAX(x_s) FILTER (WHERE keypoint_name = 'right_elbow') AS x_r_elb,
                   MAX(y_s) FILTER (WHERE keypoint_name = 'right_elbow') AS y_r_elb,
                   MAX(x_s) FILTER (WHERE keypoint_name = 'left_wrist') AS x_l_wri,
                   MAX(y_s) FILTER (WHERE keypoint_name = 'left_wrist') AS y_l_wri,
                   MAX(x_s) FILTER (WHERE keypoint_name = 'right_wrist') AS x_r_wri,
                   MAX(y_s) FILTER (WHERE keypoint_name = 'right_wrist') AS y_r_wri,
                   MAX(x_s) FILTER (WHERE keypoint_name = 'left_knee') AS x_l_knee,
                   MAX(y_s) FILTER (WHERE keypoint_name = 'left_knee') AS y_l_knee,
                   MAX(x_s) FILTER (WHERE keypoint_name = 'right_knee') AS x_r_knee,
                   MAX(y_s) FILTER (WHERE keypoint_name = 'right_knee') AS y_r_knee,
                   MAX(x_s) FILTER (WHERE keypoint_name = 'left_ankle') AS x_l_ank,
                   MAX(y_s) FILTER (WHERE keypoint_name = 'left_ankle') AS y_l_ank,
                   MAX(x_s) FILTER (WHERE keypoint_name = 'right_ankle') AS x_r_ank,
                   MAX(y_s) FILTER (WHERE keypoint_name = 'right_ankle') AS y_r_ank
            FROM smoothed_kp
            GROUP BY sid, frame_index
        ),
        -- 5. de Leva (1996) CoM számítások
        com_calc AS (
            SELECT p.*,
                   (x_l_sh + x_r_sh) / 2.0 AS x_sh_mid,
                   (y_l_sh + y_r_sh) / 2.0 AS y_sh_mid,
                   COALESCE((x_l_hip + x_r_hip) / 2.0, x_hip_c) AS x_hip_mid,
                   COALESCE((y_l_hip + y_r_hip) / 2.0, y_hip_c) AS y_hip_mid,
                   (x_l_ank + x_r_ank) / 2.0 AS x_ank_mid,
                   (y_l_ank + y_r_ank) / 2.0 AS y_ank_mid,
                   LEAST(x_l_ank, x_r_ank) AS bos_left,
                   GREATEST(x_l_ank, x_r_ank) AS bos_right,
                   ABS(x_l_ank - x_r_ank) AS bos_width
            FROM pivoted p
            WHERE x_l_ank IS NOT NULL AND x_r_ank IS NOT NULL
              AND y_l_sh IS NOT NULL AND y_r_sh IS NOT NULL
              AND (x_hip_c IS NOT NULL OR (x_l_hip IS NOT NULL AND x_r_hip IS NOT NULL))
        ),
        com_final AS (
            SELECT c.*,
                   CASE
                       WHEN x_l_knee IS NOT NULL AND x_r_knee IS NOT NULL AND x_l_elb IS NOT NULL AND x_r_elb IS NOT NULL THEN
                           0.5034 * (x_sh_mid + 0.4486 * (x_hip_mid - x_sh_mid)) +
                           0.0263 * (x_l_sh + 0.5772 * (x_l_elb - x_l_sh)) +
                           0.0263 * (x_r_sh + 0.5772 * (x_r_elb - x_r_sh)) +
                           0.0207 * (x_l_elb + 0.4574 * (COALESCE(x_l_wri, x_l_elb) - x_l_elb)) +
                           0.0207 * (x_r_elb + 0.4574 * (COALESCE(x_r_wri, x_r_elb) - x_r_elb)) +
                           0.1447 * (COALESCE(x_l_hip, x_hip_mid) + 0.4095 * (x_l_knee - COALESCE(x_l_hip, x_hip_mid))) +
                           0.1447 * (COALESCE(x_r_hip, x_hip_mid) + 0.4095 * (x_r_knee - COALESCE(x_r_hip, x_hip_mid))) +
                           0.0566 * (x_l_knee + 0.4459 * (x_l_ank - x_l_knee)) +
                           0.0566 * (x_r_knee + 0.4459 * (x_r_ank - x_r_knee))
                       ELSE
                           0.5034 * (x_sh_mid + 0.4486 * (x_hip_mid - x_sh_mid)) +
                           0.4966 * (x_hip_mid + 0.4200 * (x_ank_mid - x_hip_mid))
                       END AS x_com,
                   CASE
                       WHEN y_l_knee IS NOT NULL AND y_r_knee IS NOT NULL AND y_l_elb IS NOT NULL AND y_r_elb IS NOT NULL THEN
                           0.5034 * (y_sh_mid + 0.4486 * (y_hip_mid - y_sh_mid)) +
                           0.0263 * (y_l_sh + 0.5772 * (y_l_elb - y_l_sh)) +
                           0.0263 * (y_r_sh + 0.5772 * (y_r_elb - y_r_sh)) +
                           0.0207 * (y_l_elb + 0.4574 * (COALESCE(y_l_wri, y_l_elb) - y_l_elb)) +
                           0.0207 * (y_r_elb + 0.4574 * (COALESCE(y_r_wri, y_r_elb) - y_r_elb)) +
                           0.1447 * (COALESCE(y_l_hip, y_hip_mid) + 0.4095 * (y_l_knee - COALESCE(y_l_hip, y_hip_mid))) +
                           0.1447 * (COALESCE(y_r_hip, y_hip_mid) + 0.4095 * (y_r_knee - COALESCE(y_r_hip, y_hip_mid))) +
                           0.0566 * (y_l_knee + 0.4459 * (y_l_ank - y_l_knee)) +
                           0.0566 * (y_r_knee + 0.4459 * (y_r_ank - y_r_knee))
                       ELSE
                           0.5034 * (y_sh_mid + 0.4486 * (y_hip_mid - y_sh_mid)) +
                           0.4966 * (y_hip_mid + 0.4200 * (y_ank_mid - y_hip_mid))
                       END AS y_com
            FROM com_calc c
        ),
        -- 6. Skálázó faktor és aggregáció
        scales AS (
            SELECT sid, PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY ABS(y_ank_mid - y_sh_mid)) + 1e-6 AS ref_scale
            FROM com_final GROUP BY sid
        ),
        normalized AS (
            SELECT
                f.sid, f.frame_index,
                f.x_com / s.ref_scale AS com_x_norm,
                f.y_com / s.ref_scale AS com_y_norm,
                (f.x_com / s.ref_scale) - LAG(f.x_com / s.ref_scale) OVER w AS dx,
                (f.y_com / s.ref_scale) - LAG(f.y_com / s.ref_scale) OVER w AS dy,
                f.bos_width / s.ref_scale AS bos_w_norm,
                (f.bos_width / s.ref_scale) - LAG(f.bos_width / s.ref_scale) OVER w AS d_bos,
                (f.x_com - f.bos_left) / (f.bos_width + 1e-6) AS norm_com_pos,
                DEGREES(ATAN2(f.x_sh_mid - f.x_hip_mid, ABS(f.y_hip_mid - f.y_sh_mid) + 1e-6)) AS trunk_angle_deg
            FROM com_final f
                     JOIN scales s ON f.sid = s.sid
            WINDOW w AS (PARTITION BY f.sid ORDER BY f.frame_index)
        ),
        metrics AS (
            SELECT
                sid,
                SUM(SQRT(dx*dx + dy*dy)) AS sway_path_len,
                AVG(SQRT(dx*dx + dy*dy)) * 30.0 AS mean_vel,
                STDDEV_SAMP(trunk_angle_deg) AS hip_sd,
                AVG(ABS(d_bos)) * 30.0 AS bos_vol,
                PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY norm_com_pos) AS med_com_pos,
                VAR_SAMP(com_x_norm) AS var_x,
                VAR_SAMP(com_y_norm) AS var_y,
                COVAR_SAMP(com_x_norm, com_y_norm) AS cov_xy
            FROM normalized GROUP BY sid
        ),
        -- 7. Pontszámok generálása
        scored AS (
            SELECT
                m.sid, m.mean_vel, m.sway_path_len,
                PI() * 5.991 * SQRT(GREATEST(0.0, m.var_x * m.var_y - m.cov_xy * m.cov_xy)) AS area_ellipse,
                m.hip_sd, m.med_com_pos,
                LEAST(10.0, GREATEST(1.0, 10.0 * (1.0 - (m.mean_vel / 0.10)))) AS s_score,
                LEAST(10.0, GREATEST(1.0, 10.0 * (1.0 - (m.bos_vol / 0.08)))) AS b_score,
                LEAST(10.0, GREATEST(1.0, 10.0 * (1.0 - (ABS(m.med_com_pos - 0.5) / 0.35)))) AS sym_score
            FROM metrics m
        )
    SELECT
        s.sid,
        ROUND(CAST(0.40 * s.s_score + 0.35 * s.b_score + 0.25 * s.sym_score AS NUMERIC), 1) AS osszpontszam,
        ROUND(s.mean_vel::NUMERIC, 4),
        ROUND(s.sway_path_len::NUMERIC, 3),
        ROUND(s.area_ellipse::NUMERIC, 5),
        ROUND(s.hip_sd::NUMERIC, 2),
        ROUND(s.s_score::NUMERIC, 1),
        ROUND(s.b_score::NUMERIC, 1),
        ROUND(s.sym_score::NUMERIC, 1),
        CASE WHEN s.med_com_pos < 0.45 THEN 'Balra' WHEN s.med_com_pos > 0.55 THEN 'Jobbra' ELSE 'Középen' END,
        CASE
            WHEN (0.40 * s.s_score + 0.35 * s.b_score + 0.25 * s.sym_score) >= 8.5 THEN 'Kiváló'
            WHEN (0.40 * s.s_score + 0.35 * s.b_score + 0.25 * s.sym_score) >= 6.5 THEN 'Jó'
            ELSE 'Instabil'
            END,
        NOW()  -- EZ HIÁNYZOTT: A 'kiszamitva' oszlop értéke!
    FROM scored s
    ON CONFLICT (session_id) DO UPDATE SET
                                           osszpontszam = EXCLUDED.osszpontszam, mean_velocity = EXCLUDED.mean_velocity,
                                           sway_path = EXCLUDED.sway_path, sway_area = EXCLUDED.sway_area,
                                           torzs_ingadozas_sd = EXCLUDED.torzs_ingadozas_sd, stabilitas_pont = EXCLUDED.stabilitas_pont,
                                           alatamasztas_pont = EXCLUDED.alatamasztas_pont, szimmetria_pont = EXCLUDED.szimmetria_pont,
                                           terhelesi_irany = EXCLUDED.terhelesi_irany, ertekeles = EXCLUDED.ertekeles, kiszamitva = NOW();
END;
$$;


--
-- Name: evaluate_biomechanics(text); Type: FUNCTION; Schema: zokni; Owner: -
--

CREATE FUNCTION zokni.evaluate_biomechanics(p_session_id text DEFAULT NULL::text) RETURNS TABLE(session_id text, osszpontszam integer, mean_velocity numeric, sway_path numeric, sway_area numeric, torzs_ingadozas_sd numeric, stabilitas_pont numeric, alatamasztas_pont numeric, szimmetria_pont numeric, terhelesi_irany text, ertekeles text)
    LANGUAGE plpgsql
    AS $$
BEGIN
    RETURN QUERY
        WITH
            -- 1. Célzott kulcspontok lekérése és 0.7-es konfidenciaszűrés (Needham et al., 2021)
            raw_kp AS (
                SELECT
                    ek.session_id::TEXT AS sid,
                    ek.frame_index,
                    ek.keypoint_name,
                    CASE WHEN ek.confidence >= 0.7 THEN ek.x::DOUBLE PRECISION ELSE NULL END AS x,
                    CASE WHEN ek.confidence >= 0.7 THEN ek.y::DOUBLE PRECISION ELSE NULL END AS y
                FROM zokni.extracted_keypoints ek
                WHERE (p_session_id IS NULL OR ek.session_id = p_session_id)
                  AND ek.keypoint_name IN (
                                           'hip_center', 'left_hip', 'right_hip',
                                           'left_shoulder', 'right_shoulder',
                                           'left_elbow', 'right_elbow',
                                           'left_wrist', 'right_wrist',
                                           'left_knee', 'right_knee',
                                           'left_ankle', 'right_ankle'
                    )
            ),
            -- 2. Mozgó átlag/medián alapú kiugróérték-szűrés (JUMP_THRESHOLD = 25.0 px)
            outlier_filtered AS (
                SELECT
                    sid, frame_index, keypoint_name,
                    CASE
                        WHEN ABS(x - AVG(x) OVER w) > 25.0 THEN NULL
                        ELSE x
                        END AS x_clean,
                    CASE
                        WHEN ABS(y - AVG(y) OVER w) > 25.0 THEN NULL
                        ELSE y
                        END AS y_clean
                FROM raw_kp
                WINDOW w AS (PARTITION BY sid, keypoint_name ORDER BY frame_index ROWS BETWEEN 2 PRECEDING AND 2 FOLLOWING)
            ),
            -- 3. Nulla fázistolású (Zero-Phase) 5-pontos súlyozott aluláteresztő szűrés (Winter, 2009)
            smoothed_kp AS (
                SELECT
                    sid, frame_index, keypoint_name,
                    (
                        1.0 * COALESCE(LAG(x_clean, 2) OVER w, LAG(x_clean, 1) OVER w, x_clean) +
                        4.0 * COALESCE(LAG(x_clean, 1) OVER w, x_clean) +
                        6.0 * COALESCE(x_clean, LAG(x_clean, 1) OVER w, LEAD(x_clean, 1) OVER w) +
                        4.0 * COALESCE(LEAD(x_clean, 1) OVER w, x_clean) +
                        1.0 * COALESCE(LEAD(x_clean, 2) OVER w, LEAD(x_clean, 1) OVER w, x_clean)
                        ) / 16.0 AS x_s,
                    (
                        1.0 * COALESCE(LAG(y_clean, 2) OVER w, LAG(y_clean, 1) OVER w, y_clean) +
                        4.0 * COALESCE(LAG(y_clean, 1) OVER w, y_clean) +
                        6.0 * COALESCE(y_clean, LAG(y_clean, 1) OVER w, LEAD(y_clean, 1) OVER w) +
                        4.0 * COALESCE(LEAD(y_clean, 1) OVER w, y_clean) +
                        1.0 * COALESCE(LEAD(y_clean, 2) OVER w, LEAD(y_clean, 1) OVER w, y_clean)
                        ) / 16.0 AS y_s
                FROM outlier_filtered
                WINDOW w AS (PARTITION BY sid, keypoint_name ORDER BY frame_index)
            ),
            -- 4. Pivotálás (Képkockánként 1 sor az összes testrésszel)
            pivoted AS (
                SELECT
                    sid, frame_index,
                    MAX(x_s) FILTER (WHERE keypoint_name = 'hip_center') AS x_hip_c,
                    MAX(y_s) FILTER (WHERE keypoint_name = 'hip_center') AS y_hip_c,
                    MAX(x_s) FILTER (WHERE keypoint_name = 'left_hip') AS x_l_hip,
                    MAX(y_s) FILTER (WHERE keypoint_name = 'left_hip') AS y_l_hip,
                    MAX(x_s) FILTER (WHERE keypoint_name = 'right_hip') AS x_r_hip,
                    MAX(y_s) FILTER (WHERE keypoint_name = 'right_hip') AS y_r_hip,
                    MAX(x_s) FILTER (WHERE keypoint_name = 'left_shoulder') AS x_l_sh,
                    MAX(y_s) FILTER (WHERE keypoint_name = 'left_shoulder') AS y_l_sh,
                    MAX(x_s) FILTER (WHERE keypoint_name = 'right_shoulder') AS x_r_sh,
                    MAX(y_s) FILTER (WHERE keypoint_name = 'right_shoulder') AS y_r_sh,
                    MAX(x_s) FILTER (WHERE keypoint_name = 'left_elbow') AS x_l_elb,
                    MAX(y_s) FILTER (WHERE keypoint_name = 'left_elbow') AS y_l_elb,
                    MAX(x_s) FILTER (WHERE keypoint_name = 'right_elbow') AS x_r_elb,
                    MAX(y_s) FILTER (WHERE keypoint_name = 'right_elbow') AS y_r_elb,
                    MAX(x_s) FILTER (WHERE keypoint_name = 'left_wrist') AS x_l_wri,
                    MAX(y_s) FILTER (WHERE keypoint_name = 'left_wrist') AS y_l_wri,
                    MAX(x_s) FILTER (WHERE keypoint_name = 'right_wrist') AS x_r_wri,
                    MAX(y_s) FILTER (WHERE keypoint_name = 'right_wrist') AS y_r_wri,
                    MAX(x_s) FILTER (WHERE keypoint_name = 'left_knee') AS x_l_knee,
                    MAX(y_s) FILTER (WHERE keypoint_name = 'left_knee') AS y_l_knee,
                    MAX(x_s) FILTER (WHERE keypoint_name = 'right_knee') AS x_r_knee,
                    MAX(y_s) FILTER (WHERE keypoint_name = 'right_knee') AS y_r_knee,
                    MAX(x_s) FILTER (WHERE keypoint_name = 'left_ankle') AS x_l_ank,
                    MAX(y_s) FILTER (WHERE keypoint_name = 'left_ankle') AS y_l_ank,
                    MAX(x_s) FILTER (WHERE keypoint_name = 'right_ankle') AS x_r_ank,
                    MAX(y_s) FILTER (WHERE keypoint_name = 'right_ankle') AS y_r_ank
                FROM smoothed_kp
                GROUP BY sid, frame_index
            ),
            -- 5. de Leva (1996) szegmentális CoM és BoS számítás képkockánként
            com_calc AS (
                SELECT
                    p.*,
                    (x_l_sh + x_r_sh) / 2.0 AS x_sh_mid,
                    (y_l_sh + y_r_sh) / 2.0 AS y_sh_mid,
                    COALESCE((x_l_hip + x_r_hip) / 2.0, x_hip_c) AS x_hip_mid,
                    COALESCE((y_l_hip + y_r_hip) / 2.0, y_hip_c) AS y_hip_mid,
                    (x_l_ank + x_r_ank) / 2.0 AS x_ank_mid,
                    (y_l_ank + y_r_ank) / 2.0 AS y_ank_mid,
                    LEAST(x_l_ank, x_r_ank) AS bos_left,
                    GREATEST(x_l_ank, x_r_ank) AS bos_right,
                    ABS(x_l_ank - x_r_ank) AS bos_width
                FROM pivoted p
                WHERE x_l_ank IS NOT NULL AND x_r_ank IS NOT NULL
                  AND y_l_sh IS NOT NULL AND y_r_sh IS NOT NULL
                  AND (x_hip_c IS NOT NULL OR (x_l_hip IS NOT NULL AND x_r_hip IS NOT NULL))
            ),
            com_final AS (
                SELECT
                    c.*,
                    CASE
                        WHEN x_l_knee IS NOT NULL AND x_r_knee IS NOT NULL AND x_l_elb IS NOT NULL AND x_r_elb IS NOT NULL THEN
                            0.5034 * (x_sh_mid + 0.4486 * (x_hip_mid - x_sh_mid)) +
                            0.0263 * (x_l_sh + 0.5772 * (x_l_elb - x_l_sh)) +
                            0.0263 * (x_r_sh + 0.5772 * (x_r_elb - x_r_sh)) +
                            0.0207 * (x_l_elb + 0.4574 * (COALESCE(x_l_wri, x_l_elb) - x_l_elb)) +
                            0.0207 * (x_r_elb + 0.4574 * (COALESCE(x_r_wri, x_r_elb) - x_r_elb)) +
                            0.1447 * (COALESCE(x_l_hip, x_hip_mid) + 0.4095 * (x_l_knee - COALESCE(x_l_hip, x_hip_mid))) +
                            0.1447 * (COALESCE(x_r_hip, x_hip_mid) + 0.4095 * (x_r_knee - COALESCE(x_r_hip, x_hip_mid))) +
                            0.0566 * (x_l_knee + 0.4459 * (x_l_ank - x_l_knee)) +
                            0.0566 * (x_r_knee + 0.4459 * (x_r_ank - x_r_knee))
                        ELSE
                            0.5034 * (x_sh_mid + 0.4486 * (x_hip_mid - x_sh_mid)) +
                            0.4966 * (x_hip_mid + 0.4200 * (x_ank_mid - x_hip_mid))
                        END AS x_com,
                    CASE
                        WHEN y_l_knee IS NOT NULL AND y_r_knee IS NOT NULL AND y_l_elb IS NOT NULL AND y_r_elb IS NOT NULL THEN
                            0.5034 * (y_sh_mid + 0.4486 * (y_hip_mid - y_sh_mid)) +
                            0.0263 * (y_l_sh + 0.5772 * (y_l_elb - y_l_sh)) +
                            0.0263 * (y_r_sh + 0.5772 * (y_r_elb - y_r_sh)) +
                            0.0207 * (y_l_elb + 0.4574 * (COALESCE(y_l_wri, y_l_elb) - y_l_elb)) +
                            0.0207 * (y_r_elb + 0.4574 * (COALESCE(y_r_wri, y_r_elb) - y_r_elb)) +
                            0.1447 * (COALESCE(y_l_hip, y_hip_mid) + 0.4095 * (y_l_knee - COALESCE(y_l_hip, y_hip_mid))) +
                            0.1447 * (COALESCE(y_r_hip, y_hip_mid) + 0.4095 * (y_r_knee - COALESCE(y_r_hip, y_hip_mid))) +
                            0.0566 * (y_l_knee + 0.4459 * (y_l_ank - y_l_knee)) +
                            0.0566 * (y_r_knee + 0.4459 * (y_r_ank - y_r_knee))
                        ELSE
                            0.5034 * (y_sh_mid + 0.4486 * (y_hip_mid - y_sh_mid)) +
                            0.4966 * (y_hip_mid + 0.4200 * (y_ank_mid - y_hip_mid))
                        END AS y_com
                FROM com_calc c
            ),
            -- 6. Anatómiai skálázó faktor (Stenum et al., 2021)
            scales AS (
                SELECT
                    sid,
                    PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY ABS(y_ank_mid - y_sh_mid)) + 1e-6 AS ref_scale
                FROM com_final
                GROUP BY sid
            ),
            -- 7. Normalizálás és képkockák közötti differenciák (deriválás)
            normalized AS (
                SELECT
                    f.sid,
                    f.frame_index,
                    f.x_com / s.ref_scale AS com_x_norm,
                    f.y_com / s.ref_scale AS com_y_norm,
                    (f.x_com / s.ref_scale) - LAG(f.x_com / s.ref_scale) OVER w AS dx,
                    (f.y_com / s.ref_scale) - LAG(f.y_com / s.ref_scale) OVER w AS dy,
                    f.bos_width / s.ref_scale AS bos_w_norm,
                    (f.bos_width / s.ref_scale) - LAG(f.bos_width / s.ref_scale) OVER w AS d_bos,
                    (f.x_com - f.bos_left) / (f.bos_width + 1e-6) AS norm_com_pos,
                    DEGREES(ATAN2(f.x_sh_mid - f.x_hip_mid, ABS(f.y_hip_mid - f.y_sh_mid) + 1e-6)) AS trunk_angle_deg
                FROM com_final f
                         JOIN scales s ON f.sid = s.sid
                WINDOW w AS (PARTITION BY f.sid ORDER BY f.frame_index)
            ),
            -- 8. Prieto et al. (1996) és Horak & Nashner (1986) statisztikai aggregáció
            metrics AS (
                SELECT
                    sid,
                    SUM(SQRT(dx*dx + dy*dy)) AS sway_path_len,
                    AVG(SQRT(dx*dx + dy*dy)) * 30.0 AS mean_vel,
                    STDDEV_SAMP(trunk_angle_deg) AS hip_sd,
                    AVG(ABS(d_bos)) * 30.0 AS bos_vol,
                    PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY norm_com_pos) AS med_com_pos,
                    VAR_SAMP(com_x_norm) AS var_x,
                    VAR_SAMP(com_y_norm) AS var_y,
                    COVAR_SAMP(com_x_norm, com_y_norm) AS cov_xy
                FROM normalized
                GROUP BY sid
            ),
            -- 9. Végső pontszámok és 95%-os konfidencia-ellipszis terület (det(Cov) = var_x*var_y - cov_xy^2)
            scored AS (
                SELECT
                    m.sid,
                    m.mean_vel,
                    m.sway_path_len,
                    -- Prieto et al. (1996) ellipszis terület a kovarianciamátrix determinánsából:
                    PI() * 5.991 * SQRT(GREATEST(0.0, m.var_x * m.var_y - m.cov_xy * m.cov_xy)) AS area_ellipse,
                    m.hip_sd,
                    m.med_com_pos,
                    LEAST(100.0, GREATEST(0.0, 100.0 * (1.0 - (m.mean_vel / 0.10)))) AS s_score,
                    LEAST(100.0, GREATEST(0.0, 100.0 * (1.0 - (m.bos_vol / 0.08)))) AS b_score,
                    LEAST(100.0, GREATEST(0.0, 100.0 * (1.0 - (ABS(m.med_com_pos - 0.5) / 0.35)))) AS sym_score
                FROM metrics m
            )
        SELECT
            s.sid AS session_id,
            CAST(0.40 * s.s_score + 0.35 * s.b_score + 0.25 * s.sym_score AS INT) AS osszpontszam,
            ROUND(s.mean_vel::NUMERIC, 4) AS mean_velocity,
            ROUND(s.sway_path_len::NUMERIC, 3) AS sway_path,
            ROUND(s.area_ellipse::NUMERIC, 5) AS sway_area,
            ROUND(s.hip_sd::NUMERIC, 2) AS torzs_ingadozas_sd,
            ROUND(s.s_score::NUMERIC, 1) AS stabilitas_pont,
            ROUND(s.b_score::NUMERIC, 1) AS alatamasztas_pont,
            ROUND(s.sym_score::NUMERIC, 1) AS szimmetria_pont,
            CASE
                WHEN s.med_com_pos < 0.45 THEN 'Balra'
                WHEN s.med_com_pos > 0.55 THEN 'Jobbra'
                ELSE 'Középen'
                END AS terhelesi_irany,
            CASE
                WHEN CAST(0.40 * s.s_score + 0.35 * s.b_score + 0.25 * s.sym_score AS INT) >= 85 THEN 'Kiváló'
                WHEN CAST(0.40 * s.s_score + 0.35 * s.b_score + 0.25 * s.sym_score AS INT) >= 65 THEN 'Jó'
                ELSE 'Instabil'
                END AS ertekeles
        FROM scored s
        ORDER BY osszpontszam DESC;
END;
$$;


--
-- Name: get_keypoint_positions(text, text); Type: FUNCTION; Schema: zokni; Owner: -
--

CREATE FUNCTION zokni.get_keypoint_positions(p_session_id text DEFAULT NULL::text, p_keypoint_name text DEFAULT NULL::text) RETURNS TABLE(session_id text, frame_index integer, time_s numeric, time_ms numeric, keypoint_id integer, keypoint_name text, x numeric, y numeric, confidence numeric)
    LANGUAGE plpgsql SECURITY DEFINER
    AS $$
BEGIN
    RETURN QUERY
        WITH raw_frames AS (
            SELECT
                COALESCE(s.session_id::text, s.id::text) AS s_id,
                (frame_elem->>'frame_index')::integer AS f_idx,
                ROUND(
                        CASE
                            WHEN (s.session_data->>'duration_s') IS NOT NULL THEN
                                ((frame_elem->>'frame_index')::numeric / NULLIF((vj.json_data->>'frame_count')::numeric, 0)) * (s.session_data->>'duration_s')::numeric
                            ELSE
                                (frame_elem->>'frame_index')::numeric / 30.0
                            END, 3
                ) AS t_s,
                ROUND(
                        CASE
                            WHEN (s.session_data->>'duration_ms') IS NOT NULL THEN
                                ((frame_elem->>'frame_index')::numeric / NULLIF((vj.json_data->>'frame_count')::numeric, 0)) * (s.session_data->>'duration_ms')::numeric
                            ELSE
                                ((frame_elem->>'frame_index')::numeric / 30.0) * 1000.0
                            END, 0
                ) AS t_ms,
                frame_elem->'instances'->0 AS first_instance
            FROM zokni.video_json vj
                     JOIN zokni.sessions s ON s.id = vj.id
                     CROSS JOIN LATERAL jsonb_array_elements(vj.json_data->'keypoints') AS frame_elem
            WHERE jsonb_array_length(frame_elem->'instances') > 0
              AND (p_session_id IS NULL OR s.session_id = p_session_id OR s.id::text = p_session_id)
        ),
             coco_points AS (
                 SELECT
                     rf.s_id, rf.f_idx, rf.t_s, rf.t_ms,
                     km.kp_idx::integer AS kp_id, km.kp_name,
                     ROUND((kp.kp_elem->>0)::numeric, 2) AS pos_x,
                     ROUND((kp.kp_elem->>1)::numeric, 2) AS pos_y,
                     ROUND((rf.first_instance->'keypoint_scores'->>(km.kp_idx::integer))::numeric, 4) AS conf
                 FROM raw_frames rf
                          CROSS JOIN LATERAL jsonb_array_elements(rf.first_instance->'keypoints') WITH ORDINALITY AS kp(kp_elem, ord_idx)
                          JOIN (VALUES
                                    (0,  'nose'),
                                    (5,  'left_shoulder'),  (6,  'right_shoulder'),
                                    (7,  'left_elbow'),     (8,  'right_elbow'),
                                    (9,  'left_wrist'),     (10, 'right_wrist'),
                                    (11, 'left_hip'),       (12, 'right_hip'),
                                    (13, 'left_knee'),      (14, 'right_knee'),
                                    (15, 'left_ankle'),     (16, 'right_ankle')
                 ) AS km(kp_idx, kp_name) ON km.kp_idx = (kp.ord_idx - 1)

                 UNION ALL

                 -- Virtuális 'neck' (18): a bal (5) és jobb (6) váll felezőpontja
                 SELECT
                     rf.s_id, rf.f_idx, rf.t_s, rf.t_ms,
                     18 AS kp_id, 'neck'::text AS kp_name,
                     ROUND((((rf.first_instance->'keypoints'->5->>0)::numeric + (rf.first_instance->'keypoints'->6->>0)::numeric) / 2.0), 2),
                     ROUND((((rf.first_instance->'keypoints'->5->>1)::numeric + (rf.first_instance->'keypoints'->6->>1)::numeric) / 2.0), 2),
                     ROUND((((rf.first_instance->'keypoint_scores'->>5)::numeric + (rf.first_instance->'keypoint_scores'->>6)::numeric) / 2.0), 4)
                 FROM raw_frames rf

                 UNION ALL

                 -- Virtuális 'hip_center' (19): a bal (11) és jobb (12) csípő felezőpontja
                 SELECT
                     rf.s_id, rf.f_idx, rf.t_s, rf.t_ms,
                     19 AS kp_id, 'hip_center'::text AS kp_name,
                     ROUND((((rf.first_instance->'keypoints'->11->>0)::numeric + (rf.first_instance->'keypoints'->12->>0)::numeric) / 2.0), 2),
                     ROUND((((rf.first_instance->'keypoints'->11->>1)::numeric + (rf.first_instance->'keypoints'->12->>1)::numeric) / 2.0), 2),
                     ROUND((((rf.first_instance->'keypoint_scores'->>11)::numeric + (rf.first_instance->'keypoint_scores'->>12)::numeric) / 2.0), 4)
                 FROM raw_frames rf
             )
        SELECT cp.s_id, cp.f_idx, cp.t_s, cp.t_ms, cp.kp_id, cp.kp_name, cp.pos_x, cp.pos_y, cp.conf
        FROM coco_points cp
        WHERE (p_keypoint_name IS NULL OR cp.kp_name = p_keypoint_name)
        ORDER BY cp.s_id, cp.f_idx, cp.kp_id;
END;
$$;


--
-- Name: populate_extracted_keypoints(text); Type: PROCEDURE; Schema: zokni; Owner: -
--

CREATE PROCEDURE zokni.populate_extracted_keypoints(IN p_session_id text DEFAULT NULL::text)
    LANGUAGE plpgsql SECURITY DEFINER
    AS $$
BEGIN
    INSERT INTO zokni.extracted_keypoints (
        session_id, frame_index, time_s, time_ms, keypoint_id, keypoint_name, x, y, confidence
    )
    SELECT
        session_id, frame_index, time_s, time_ms, keypoint_id, keypoint_name, x, y, confidence
    FROM zokni.get_keypoint_positions(p_session_id, NULL)
    ON CONFLICT (session_id, frame_index, keypoint_id)
        DO UPDATE SET
                      time_s = EXCLUDED.time_s,
                      time_ms = EXCLUDED.time_ms,
                      x = EXCLUDED.x,
                      y = EXCLUDED.y,
                      confidence = EXCLUDED.confidence;
END;
$$;


--
-- Name: trg_extract_keypoints_fn(); Type: FUNCTION; Schema: zokni; Owner: -
--

CREATE FUNCTION zokni.trg_extract_keypoints_fn() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    AS $$
BEGIN
    CALL zokni.populate_extracted_keypoints(NEW.id::text);
    RETURN NEW;
END;
$$;


--
-- Name: trigger_calc_biomechanics(); Type: FUNCTION; Schema: zokni; Owner: -
--

CREATE FUNCTION zokni.trigger_calc_biomechanics() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    -- JAVÍTVA: A trigger most már az 'inserted' állapotot figyeli!
    IF NEW.analysis_status = 'inserted' AND (OLD.analysis_status IS DISTINCT FROM 'inserted') THEN
        PERFORM zokni.evaluate_and_store_biomechanics(NEW.session_id);
    END IF;
    RETURN NEW;
END;
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: athletes; Type: TABLE; Schema: zokni; Owner: -
--

CREATE TABLE zokni.athletes (
    id uuid NOT NULL,
    age integer,
    gender text,
    height_cm numeric,
    weight_kg numeric,
    created_at timestamp with time zone DEFAULT now(),
    is_deleted boolean DEFAULT false
);


--
-- Name: athletes; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.athletes WITH (security_invoker='true') AS
 SELECT id,
    age,
    gender,
    height_cm,
    weight_kg,
    created_at
   FROM zokni.athletes
  WHERE (is_deleted = false);


--
-- Name: sessions; Type: TABLE; Schema: zokni; Owner: -
--

CREATE TABLE zokni.sessions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    session_id text NOT NULL,
    patient_id text NOT NULL,
    is_deleted boolean DEFAULT false,
    session_data jsonb NOT NULL,
    video_url text,
    analysis_status text DEFAULT 'pending'::text,
    created_at timestamp with time zone DEFAULT now(),
    processing_started timestamp with time zone
);


--
-- Name: deleted_sessions; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.deleted_sessions WITH (security_invoker='true') AS
 SELECT id,
    session_id,
    patient_id,
    session_data
   FROM zokni.sessions
  WHERE (is_deleted = true);


--
-- Name: exercises; Type: TABLE; Schema: zokni; Owner: -
--

CREATE TABLE zokni.exercises (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name text NOT NULL,
    category text,
    description text,
    instructions text,
    video_url text,
    is_global boolean DEFAULT false,
    created_by uuid,
    created_at timestamp with time zone DEFAULT now(),
    is_deleted boolean DEFAULT false
);


--
-- Name: exercises; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.exercises WITH (security_invoker='true') AS
 SELECT id,
    name,
    category,
    description,
    instructions,
    video_url,
    is_global,
    created_by,
    created_at,
    is_deleted
   FROM zokni.exercises
  WHERE (is_deleted = false);


--
-- Name: sessions; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.sessions WITH (security_invoker='true') AS
 SELECT id,
    session_id,
    patient_id,
    session_data,
    video_url,
    analysis_status,
    is_deleted
   FROM zokni.sessions
  WHERE (is_deleted = false);


--
-- Name: biomechanics_results; Type: TABLE; Schema: zokni; Owner: -
--

CREATE TABLE zokni.biomechanics_results (
    session_id text NOT NULL,
    osszpontszam numeric,
    mean_velocity numeric,
    sway_path numeric,
    sway_area numeric,
    torzs_ingadozas_sd numeric,
    stabilitas_pont numeric,
    alatamasztas_pont numeric,
    szimmetria_pont numeric,
    terhelesi_irany text,
    ertekeles text,
    kiszamitva timestamp with time zone DEFAULT now()
);


--
-- Name: extracted_keypoints; Type: TABLE; Schema: zokni; Owner: -
--

CREATE TABLE zokni.extracted_keypoints (
    session_id text NOT NULL,
    frame_index integer NOT NULL,
    time_s numeric,
    time_ms numeric,
    keypoint_id integer NOT NULL,
    keypoint_name text NOT NULL,
    x numeric,
    y numeric,
    confidence numeric
);


--
-- Name: video_json; Type: TABLE; Schema: zokni; Owner: -
--

CREATE TABLE zokni.video_json (
    id uuid NOT NULL,
    json_data jsonb NOT NULL
);


--
-- Name: athletes athletes_pkey; Type: CONSTRAINT; Schema: zokni; Owner: -
--

ALTER TABLE ONLY zokni.athletes
    ADD CONSTRAINT athletes_pkey PRIMARY KEY (id);


--
-- Name: biomechanics_results biomechanics_results_pkey; Type: CONSTRAINT; Schema: zokni; Owner: -
--

ALTER TABLE ONLY zokni.biomechanics_results
    ADD CONSTRAINT biomechanics_results_pkey PRIMARY KEY (session_id);


--
-- Name: exercises exercises_pkey; Type: CONSTRAINT; Schema: zokni; Owner: -
--

ALTER TABLE ONLY zokni.exercises
    ADD CONSTRAINT exercises_pkey PRIMARY KEY (id);


--
-- Name: extracted_keypoints extracted_keypoints_pkey; Type: CONSTRAINT; Schema: zokni; Owner: -
--

ALTER TABLE ONLY zokni.extracted_keypoints
    ADD CONSTRAINT extracted_keypoints_pkey PRIMARY KEY (session_id, frame_index, keypoint_id);


--
-- Name: sessions sessions_session_id_key; Type: CONSTRAINT; Schema: zokni; Owner: -
--

ALTER TABLE ONLY zokni.sessions
    ADD CONSTRAINT sessions_session_id_key UNIQUE (session_id);


--
-- Name: video_json video_json_pkey; Type: CONSTRAINT; Schema: zokni; Owner: -
--

ALTER TABLE ONLY zokni.video_json
    ADD CONSTRAINT video_json_pkey PRIMARY KEY (id);


--
-- Name: idx_extracted_kp_name; Type: INDEX; Schema: zokni; Owner: -
--

CREATE INDEX idx_extracted_kp_name ON zokni.extracted_keypoints USING btree (session_id, keypoint_name);


--
-- Name: athletes athlete_delete_trg; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER athlete_delete_trg INSTEAD OF DELETE ON public.athletes FOR EACH ROW EXECUTE FUNCTION public.athlete_delete_trg();


--
-- Name: athletes athlete_insert_trg; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER athlete_insert_trg INSTEAD OF INSERT ON public.athletes FOR EACH ROW EXECUTE FUNCTION public.athlete_insert_trg();


--
-- Name: video_json trg_after_video_json_insert; Type: TRIGGER; Schema: zokni; Owner: -
--

CREATE TRIGGER trg_after_video_json_insert AFTER INSERT OR UPDATE ON zokni.video_json FOR EACH ROW EXECUTE FUNCTION zokni.trg_extract_keypoints_fn();


--
-- Name: sessions trg_calc_biomechanics; Type: TRIGGER; Schema: zokni; Owner: -
--

CREATE TRIGGER trg_calc_biomechanics AFTER UPDATE OF analysis_status ON zokni.sessions FOR EACH ROW EXECUTE FUNCTION zokni.trigger_calc_biomechanics();


--
-- Name: athletes athletes_id_fkey; Type: FK CONSTRAINT; Schema: zokni; Owner: -
--

ALTER TABLE ONLY zokni.athletes
    ADD CONSTRAINT athletes_id_fkey FOREIGN KEY (id) REFERENCES auth.users(id);


--
-- Name: biomechanics_results biomechanics_results_session_id_fkey; Type: FK CONSTRAINT; Schema: zokni; Owner: -
--

ALTER TABLE ONLY zokni.biomechanics_results
    ADD CONSTRAINT biomechanics_results_session_id_fkey FOREIGN KEY (session_id) REFERENCES zokni.sessions(session_id) ON DELETE CASCADE;


--
-- Name: exercises exercises_created_by_fkey; Type: FK CONSTRAINT; Schema: zokni; Owner: -
--

ALTER TABLE ONLY zokni.exercises
    ADD CONSTRAINT exercises_created_by_fkey FOREIGN KEY (created_by) REFERENCES auth.users(id);


--
-- Name: exercises Gyakorlatok megtekintése; Type: POLICY; Schema: zokni; Owner: -
--

CREATE POLICY "Gyakorlatok megtekintése" ON zokni.exercises FOR SELECT TO authenticated USING (((is_global = true) OR (created_by = auth.uid())));


--
-- Name: exercises Saját gyakorlat létrehozása; Type: POLICY; Schema: zokni; Owner: -
--

CREATE POLICY "Saját gyakorlat létrehozása" ON zokni.exercises FOR INSERT TO authenticated WITH CHECK ((created_by = auth.uid()));


--
-- Name: exercises Saját gyakorlat módosítása; Type: POLICY; Schema: zokni; Owner: -
--

CREATE POLICY "Saját gyakorlat módosítása" ON zokni.exercises FOR UPDATE TO authenticated USING ((created_by = auth.uid()));


--
-- Name: exercises Saját gyakorlat törlése; Type: POLICY; Schema: zokni; Owner: -
--

CREATE POLICY "Saját gyakorlat törlése" ON zokni.exercises FOR DELETE TO authenticated USING ((created_by = auth.uid()));


--
-- Name: extracted_keypoints Saját kulcspontok olvasása; Type: POLICY; Schema: zokni; Owner: -
--

CREATE POLICY "Saját kulcspontok olvasása" ON zokni.extracted_keypoints FOR SELECT TO authenticated USING ((EXISTS ( SELECT 1
   FROM zokni.sessions s
  WHERE (((s.session_id = extracted_keypoints.session_id) OR ((s.id)::text = extracted_keypoints.session_id)) AND (s.patient_id = (auth.uid())::text)))));


--
-- Name: sessions Saját sessionök beszúrása; Type: POLICY; Schema: zokni; Owner: -
--

CREATE POLICY "Saját sessionök beszúrása" ON zokni.sessions FOR INSERT TO authenticated WITH CHECK (((auth.uid())::text = patient_id));


--
-- Name: sessions Saját sessionök megtekintése; Type: POLICY; Schema: zokni; Owner: -
--

CREATE POLICY "Saját sessionök megtekintése" ON zokni.sessions FOR SELECT TO authenticated USING (((auth.uid())::text = patient_id));


--
-- Name: sessions Saját sessionök módosítása; Type: POLICY; Schema: zokni; Owner: -
--

CREATE POLICY "Saját sessionök módosítása" ON zokni.sessions FOR UPDATE TO authenticated USING (((auth.uid())::text = patient_id));


--
-- Name: video_json Saját video_json olvasása; Type: POLICY; Schema: zokni; Owner: -
--

CREATE POLICY "Saját video_json olvasása" ON zokni.video_json FOR SELECT TO authenticated USING ((EXISTS ( SELECT 1
   FROM zokni.sessions s
  WHERE ((s.id = video_json.id) AND (s.patient_id = (auth.uid())::text)))));


--
-- Name: athletes Sportoló láthatja a saját profilját; Type: POLICY; Schema: zokni; Owner: -
--

CREATE POLICY "Sportoló láthatja a saját profilját" ON zokni.athletes FOR SELECT USING ((auth.uid() = id));


--
-- Name: athletes Sportoló létrehozhatja a saját profilját; Type: POLICY; Schema: zokni; Owner: -
--

CREATE POLICY "Sportoló létrehozhatja a saját profilját" ON zokni.athletes FOR INSERT WITH CHECK ((auth.uid() = id));


--
-- Name: athletes Sportoló módosíthatja a saját profilját; Type: POLICY; Schema: zokni; Owner: -
--

CREATE POLICY "Sportoló módosíthatja a saját profilját" ON zokni.athletes FOR UPDATE USING ((auth.uid() = id));


--
-- Name: athletes; Type: ROW SECURITY; Schema: zokni; Owner: -
--

ALTER TABLE zokni.athletes ENABLE ROW LEVEL SECURITY;

--
-- Name: exercises; Type: ROW SECURITY; Schema: zokni; Owner: -
--

ALTER TABLE zokni.exercises ENABLE ROW LEVEL SECURITY;

--
-- Name: extracted_keypoints; Type: ROW SECURITY; Schema: zokni; Owner: -
--

ALTER TABLE zokni.extracted_keypoints ENABLE ROW LEVEL SECURITY;

--
-- Name: sessions; Type: ROW SECURITY; Schema: zokni; Owner: -
--

ALTER TABLE zokni.sessions ENABLE ROW LEVEL SECURITY;

--
-- Name: video_json; Type: ROW SECURITY; Schema: zokni; Owner: -
--

ALTER TABLE zokni.video_json ENABLE ROW LEVEL SECURITY;

--
-- PostgreSQL database dump complete
--



-- Korabbi migration jogosultsagai (a pg_dump --no-privileges miatt potolva)
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

-- Az uj tablakkal kapcsolatos minimalis jogosultsagok a szerveroldali feldolgozashoz
GRANT USAGE ON SCHEMA zokni TO service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE zokni.biomechanics_results, zokni.extracted_keypoints, zokni.video_json TO service_role;
GRANT SELECT ON TABLE zokni.extracted_keypoints, zokni.video_json TO authenticated;
-- A biomechanics_results kozvetlen authenticated olvasasahoz kulon RLS policy szukseges.

-- Figyelem: a korabbi anon/ authenticated jogosultsagok megmaradtak; felulvizsgalandoak.
