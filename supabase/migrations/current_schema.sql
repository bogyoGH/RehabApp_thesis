--
-- PostgreSQL database dump
--

\restrict F2qwKCtXiEotdP3LQmtNoYqmRJczrqGqMAhIfIVgiQ5hFfSQ6gjvjbg4ALfmG3Z

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

CREATE SCHEMA public;


--
-- Name: SCHEMA public; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON SCHEMA public IS 'standard public schema';


--
-- Name: zokni; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA zokni;


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

CREATE FUNCTION public.evaluate_biomechanics(p_session_id text DEFAULT NULL::text) RETURNS TABLE(session_id text, osszpontszam integer, mean_velocity numeric, sway_path numeric, sway_area numeric, torzs_ingadozas_sd numeric, stabilitas_pont numeric, alatamasztas_pont numeric, szimmetria_pont numeric, terhelesi_irany text, ertekeles text, torzsszog_idosor jsonb)
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
-- A meglévő pontszámítás és statisztikai aggregáció változatlan.
            trunk_angle_series AS (
                SELECT
                    sid,
                    jsonb_agg(
                        jsonb_build_object(
                            'frame_index', frame_index,
                            'time_s', ROUND((frame_index::numeric / 30.0), 3),
                            'angle_deg', ROUND(trunk_angle_deg::numeric, 3)
                        ) ORDER BY frame_index
                    ) FILTER (WHERE trunk_angle_deg IS NOT NULL) AS samples
                FROM normalized
                GROUP BY sid
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
                END AS ertekeles,
            COALESCE(ts.samples, '[]'::jsonb) AS torzsszog_idosor
        FROM scored s
                 LEFT JOIN trunk_angle_series ts ON ts.sid = s.sid
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
-- Name: auto_evaluate_sock(); Type: FUNCTION; Schema: zokni; Owner: -
--

CREATE FUNCTION zokni.auto_evaluate_sock() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    -- Csak akkor induljon, ha van feldolgozható
    -- adat mindkét zoknitól.
    IF jsonb_typeof(
               NEW.session_data #> '{sensors,left,data}'
       ) = 'array'
        AND jsonb_typeof(
                    NEW.session_data #> '{sensors,right,data}'
            ) = 'array'
        AND jsonb_array_length(
                    NEW.session_data #> '{sensors,left,data}'
            ) > 1
        AND jsonb_array_length(
                    NEW.session_data #> '{sensors,right,data}'
            ) > 1
    THEN
        PERFORM zokni.evaluate_sock_biomechanics(
                NEW.session_id
                );
    END IF;

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
-- Name: evaluate_combined_biomechanics(text); Type: FUNCTION; Schema: zokni; Owner: -
--

CREATE FUNCTION zokni.evaluate_combined_biomechanics(p_session_id text) RETURNS void
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_video_sd NUMERIC;
    v_sock_sd NUMERIC;
    v_status TEXT;
    v_insight TEXT;
BEGIN
    -- Videós törzsingadozás szórásának kiolvasása
    SELECT torzs_ingadozas_sd INTO v_video_sd
    FROM zokni.biomechanics_results
    WHERE session_id = p_session_id;

    -- Zoknis talpi eredő szórás kiolvasása (a JSON-ből)
    SELECT (combined_metrics->>'resultant_sd_mean')::NUMERIC INTO v_sock_sd
    FROM zokni.sock_analysis_results
    WHERE session_id = p_session_id;

    -- Ha valamelyik még hiányzik, csendben kilépünk (a másik trigger majd elindítja)
    IF v_video_sd IS NULL OR v_sock_sd IS NULL THEN
        RETURN;
    END IF;

    -- LOGIKA A FELHASZNÁLÓI VISSZAJELZÉSHEZ
    -- (A küszöbértékeket a saját adataid alapján érdemes finomhangolni!)
    IF v_video_sd < 3.0 AND v_sock_sd > 0.005 THEN
        v_status := 'Sikeres kompenzáció';
        v_insight := 'A felsőtested stabil maradt, de a talpadon látszik, hogy a bokád folyamatos mikromozgásokkal dolgozott az egyensúlyod megtartásán.';
    ELSIF v_video_sd >= 3.0 AND v_sock_sd > 0.005 THEN
        v_status := 'Túlterhelt kompenzáció';
        v_insight := 'A talpad és a bokád már nem tudta lekövetni a kilengéseket, ezért a felsőtested is jelentősen kibillent.';
    ELSIF v_video_sd < 3.0 AND v_sock_sd <= 0.005 THEN
        v_status := 'Tökéletes egyensúly';
        v_insight := 'Kiváló stabilitás: sem a felsőtested, sem a bokád nem kényszerült extra korrekciókra.';
    ELSE
        v_status := 'Kötött bokaizület';
        v_insight := 'A talpad/bokád nem végzett elég kompenzációs mozgást, emiatt a felsőtested kénytelen volt nagy kilengésekkel korrigálni.';
    END IF;

    -- Mentés az adatbázisba
    INSERT INTO zokni.combined_analysis_results (
        session_id, video_trunk_sd, sock_resultant_sd, compensation_status, insight_hu
    ) VALUES (
                 p_session_id, v_video_sd, v_sock_sd, v_status, v_insight
             )
    ON CONFLICT (session_id) DO UPDATE SET
                                           video_trunk_sd = EXCLUDED.video_trunk_sd,
                                           sock_resultant_sd = EXCLUDED.sock_resultant_sd,
                                           compensation_status = EXCLUDED.compensation_status,
                                           insight_hu = EXCLUDED.insight_hu,
                                           calculated_at = NOW();
END;
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: sock_analysis_results; Type: TABLE; Schema: zokni; Owner: -
--

CREATE TABLE zokni.sock_analysis_results (
    session_id text NOT NULL,
    left_metrics jsonb,
    right_metrics jsonb,
    combined_metrics jsonb,
    left_cop_series jsonb,
    right_cop_series jsonb,
    stability_score double precision,
    stability_category text,
    analysis_status text DEFAULT 'pending'::text NOT NULL,
    algorithm_version text,
    calculated_at timestamp with time zone DEFAULT now(),
    CONSTRAINT sock_analysis_status_check CHECK ((analysis_status = ANY (ARRAY['pending'::text, 'processing'::text, 'completed'::text, 'failed'::text]))),
    CONSTRAINT sock_stability_score_check CHECK (((stability_score IS NULL) OR ((stability_score >= (1)::double precision) AND (stability_score <= (10)::double precision))))
);


--
-- Name: evaluate_sock_biomechanics(text); Type: FUNCTION; Schema: zokni; Owner: -
--

CREATE FUNCTION zokni.evaluate_sock_biomechanics(p_session_id text) RETURNS zokni.sock_analysis_results
    LANGUAGE plpgsql
    SET search_path TO 'pg_catalog', 'zokni'
    AS $$
DECLARE
    v_doc JSONB;
    v_result zokni.sock_analysis_results%ROWTYPE;
    v_left JSONB;
    v_right JSONB;
    v_left_series JSONB;
    v_right_series JSONB;
    v_start DOUBLE PRECISION;
    v_end DOUBLE PRECISION;
    v_points INTEGER;
BEGIN
    SELECT s.session_data INTO v_doc
    FROM zokni.sessions AS s
    WHERE s.session_id = p_session_id AND s.is_deleted IS NOT TRUE;

    IF v_doc IS NULL THEN
        RAISE EXCEPTION 'Session not found or deleted: %', p_session_id;
    END IF;
    IF jsonb_typeof(v_doc #> '{sensors,left,data}') IS DISTINCT FROM 'array'
        OR jsonb_typeof(v_doc #> '{sensors,right,data}') IS DISTINCT FROM 'array' THEN
        RAISE EXCEPTION 'Session % has no left/right sensor data arrays', p_session_id;
    END IF;

    -- Both feet use the overlapping host-time interval. Source samples
    -- are averaged when they share the same host_time_ms.
    WITH raw AS MATERIALIZED (
        SELECT side_name AS side,
               (sample ->> 'host_time_ms')::DOUBLE PRECISION AS t_ms,
               (sample #>> '{pressure,heel}')::DOUBLE PRECISION AS heel,
               (sample #>> '{pressure,met_1}')::DOUBLE PRECISION AS met1,
               (sample #>> '{pressure,met_5}')::DOUBLE PRECISION AS met5
        FROM (VALUES ('left'), ('right')) sides(side_name)
                 CROSS JOIN LATERAL jsonb_array_elements(
                v_doc #> ARRAY['sensors', side_name, 'data']
                                    ) AS j(sample)
        WHERE jsonb_typeof(sample -> 'host_time_ms') = 'number'
          AND jsonb_typeof(sample #> '{pressure,heel}') = 'number'
          AND jsonb_typeof(sample #> '{pressure,met_1}') = 'number'
          AND jsonb_typeof(sample #> '{pressure,met_5}') = 'number'
    ),
         dedup AS MATERIALIZED (
             SELECT side, t_ms, AVG(heel) AS heel, AVG(met1) AS met1,
                    AVG(met5) AS met5
             FROM raw
             WHERE t_ms >= 0 AND heel >= 0 AND met1 >= 0 AND met5 >= 0
             GROUP BY side, t_ms
         ),
         limits AS (
             SELECT GREATEST(MIN(t_ms) FILTER (WHERE side = 'left'),
                             MIN(t_ms) FILTER (WHERE side = 'right')) AS start_ms,
                    LEAST(MAX(t_ms) FILTER (WHERE side = 'left'),
                          MAX(t_ms) FILTER (WHERE side = 'right')) AS end_ms,
                    COUNT(*) FILTER (WHERE side = 'left') AS left_count,
                    COUNT(*) FILTER (WHERE side = 'right') AS right_count
             FROM dedup
         ),
         grid AS MATERIALIZED (
             SELECT l.start_ms + n * (1000.0 / 30.0) AS t_ms,
                    n / 30.0 AS time_s,
                    side_name AS side
             FROM limits l
                      CROSS JOIN LATERAL generate_series(
                     0,
                     GREATEST(0, FLOOR((l.end_ms - l.start_ms) * 30.0 / 1000.0)::INTEGER)
                                         ) n
                      CROSS JOIN (VALUES ('left'),('right')) sides(side_name)
             WHERE l.start_ms IS NOT NULL AND l.end_ms > l.start_ms
               AND l.left_count >= 2 AND l.right_count >= 2
         ),
         neighbors AS MATERIALIZED (
             SELECT g.side, g.t_ms, g.time_s,
                    before_row.t_ms AS prev_t,
                    after_row.t_ms AS next_t,
                    before_row.heel AS prev_heel, after_row.heel AS next_heel,
                    before_row.met1 AS prev_met1, after_row.met1 AS next_met1,
                    before_row.met5 AS prev_met5, after_row.met5 AS next_met5
             FROM grid g
                      LEFT JOIN LATERAL (
                 SELECT d.* FROM dedup d
                 WHERE d.side = g.side AND d.t_ms <= g.t_ms
                 ORDER BY d.t_ms DESC LIMIT 1
                 ) before_row ON TRUE
                      LEFT JOIN LATERAL (
                 SELECT d.* FROM dedup d
                 WHERE d.side = g.side AND d.t_ms >= g.t_ms
                 ORDER BY d.t_ms ASC LIMIT 1
                 ) after_row ON TRUE
         ),
         interpolated AS MATERIALIZED (
             SELECT side, time_s,
                    prev_heel + (next_heel - prev_heel)
                        * COALESCE((t_ms - prev_t) / NULLIF(next_t - prev_t, 0), 0) AS heel,
                    prev_met1 + (next_met1 - prev_met1)
                        * COALESCE((t_ms - prev_t) / NULLIF(next_t - prev_t, 0), 0) AS met1,
                    prev_met5 + (next_met5 - prev_met5)
                        * COALESCE((t_ms - prev_t) / NULLIF(next_t - prev_t, 0), 0) AS met5
             FROM neighbors
             WHERE prev_t IS NOT NULL AND next_t IS NOT NULL
               AND next_t - prev_t <= 250.0 -- do not bridge long gaps
         ),
         proxy AS MATERIALIZED (
             SELECT side, time_s, heel, met1, met5,
                    (-0.5 * met1 + 0.5 * met5) /
                    NULLIF(heel + met1 + met5, 0) AS x,
                    (met1 + met5) /
                    NULLIF(heel + met1 + met5, 0) AS y
             FROM interpolated
             WHERE heel + met1 + met5 > 0
         ),
         steps AS (
             SELECT *, LAG(x) OVER (PARTITION BY side ORDER BY time_s) AS px,
                    LAG(y) OVER (PARTITION BY side ORDER BY time_s) AS py,
                    LAG(time_s) OVER (PARTITION BY side ORDER BY time_s) AS pt
             FROM proxy
         ),
         aggregates AS (
             SELECT side, COUNT(*) AS sample_count,
                    MIN(time_s) AS start_s, MAX(time_s) AS end_s,
                    STDDEV_SAMP(x) AS ml_sd, STDDEV_SAMP(y) AS ap_sd,
                    MAX(x) - MIN(x) AS ml_range,
                    MAX(y) - MIN(y) AS ap_range,
                    SUM(CASE WHEN px IS NOT NULL AND time_s-pt <= 0.10
                                 THEN SQRT(POWER(x-px,2) + POWER(y-py,2))
                             ELSE 0 END) AS path_length,
                    AVG(heel / NULLIF(heel+met1+met5,0)) AS heel_rel_mean,
                    AVG(met1 / NULLIF(heel+met1+met5,0)) AS met1_rel_mean,
                    AVG(met5 / NULLIF(heel+met1+met5,0)) AS met5_rel_mean
             FROM steps
             GROUP BY side
         ),
         metrics AS (
             SELECT side, jsonb_build_object(
                     'sample_count', sample_count,
                     'duration_s', end_s - start_s,
                     'ml_sd', ml_sd, 'ap_sd', ap_sd,
                     'resultant_sd', SQRT(POWER(ml_sd,2)+POWER(ap_sd,2)),
                     'ml_range', ml_range, 'ap_range', ap_range,
                     'range_resultant', SQRT(POWER(ml_range,2)+POWER(ap_range,2)),
                     'path_length', path_length,
                     'mean_velocity', path_length / NULLIF(end_s-start_s,0),
                     'heel_relative_mean', heel_rel_mean,
                     'met_1_relative_mean', met1_rel_mean,
                     'met_5_relative_mean', met5_rel_mean
                          ) AS data
             FROM aggregates
         ),
         series AS (
             SELECT side, jsonb_agg(
                     jsonb_build_object('time_s',ROUND(time_s::NUMERIC,4),
                                        'x',x,'y',y) ORDER BY time_s
                          ) AS data
             FROM proxy
             GROUP BY side
         )
    SELECT
        (SELECT data FROM metrics WHERE side='left'),
        (SELECT data FROM metrics WHERE side='right'),
        (SELECT data FROM series WHERE side='left'),
        (SELECT data FROM series WHERE side='right'),
        (SELECT start_ms FROM limits),
        (SELECT end_ms FROM limits),
        (SELECT COUNT(*)::INTEGER FROM grid WHERE side='left')
    INTO v_left, v_right, v_left_series, v_right_series,
        v_start, v_end, v_points;

    IF v_left IS NULL OR v_right IS NULL OR v_points < 2 THEN
        RAISE EXCEPTION 'Not enough overlapping valid sock data for session %',
            p_session_id;
    END IF;

    INSERT INTO zokni.sock_analysis_results (
        session_id, left_metrics, right_metrics, combined_metrics,
        left_cop_series, right_cop_series, stability_score,
        stability_category, analysis_status, algorithm_version, calculated_at
    ) VALUES (
                 p_session_id, v_left, v_right,
                 jsonb_build_object(
                         'target_hz',30,
                         'common_start_host_time_ms',v_start,
                         'common_end_host_time_ms',v_end,
                         'resampled_points_per_side',v_points,
                         'resultant_sd_mean',( (v_left->>'resultant_sd')::DOUBLE PRECISION
                     + (v_right->>'resultant_sd')::DOUBLE PRECISION )/2.0,
                         'path_length_mean',( (v_left->>'path_length')::DOUBLE PRECISION
                     + (v_right->>'path_length')::DOUBLE PRECISION )/2.0,
                         'mean_velocity_mean',( (v_left->>'mean_velocity')::DOUBLE PRECISION
                     + (v_right->>'mean_velocity')::DOUBLE PRECISION )/2.0,
                         'ml_variability_asymmetry', ABS(
                                                             (v_left->>'ml_sd')::DOUBLE PRECISION - (v_right->>'ml_sd')::DOUBLE PRECISION
                                                     ) / NULLIF(
                                                             (v_left->>'ml_sd')::DOUBLE PRECISION + (v_right->>'ml_sd')::DOUBLE PRECISION, 0
                                                         ),
                         'left_forefoot_fraction',
                         (v_left->>'met_1_relative_mean')::DOUBLE PRECISION +
                         (v_left->>'met_5_relative_mean')::DOUBLE PRECISION,
                         'right_forefoot_fraction',
                         (v_right->>'met_1_relative_mean')::DOUBLE PRECISION +
                         (v_right->>'met_5_relative_mean')::DOUBLE PRECISION,
                         'units','relative_unitless',
                         'method','three_sensor_relative_cop_proxy',
                         'limitations','Not calibrated CoP or physical plantar pressure; no global bilateral CoP',
                         'raw_signal_mode',v_doc->>'sensor_value_mode'
                 ),
                 v_left_series, v_right_series,
                 NULL, NULL, 'completed', 'sock_complement_v1', NOW()
             )
    ON CONFLICT (session_id) DO UPDATE SET
                                           left_metrics=EXCLUDED.left_metrics,
                                           right_metrics=EXCLUDED.right_metrics,
                                           combined_metrics=EXCLUDED.combined_metrics,
                                           left_cop_series=EXCLUDED.left_cop_series,
                                           right_cop_series=EXCLUDED.right_cop_series,
                                           stability_score=EXCLUDED.stability_score,
                                           stability_category=EXCLUDED.stability_category,
                                           analysis_status=EXCLUDED.analysis_status,
                                           algorithm_version=EXCLUDED.algorithm_version,
                                           calculated_at=EXCLUDED.calculated_at
    RETURNING * INTO v_result;

    RETURN v_result;
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
-- Name: run_combined_trigger(); Type: FUNCTION; Schema: zokni; Owner: -
--

CREATE FUNCTION zokni.run_combined_trigger() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    PERFORM zokni.evaluate_combined_biomechanics(NEW.session_id);
    RETURN NEW;
END;
$$;


--
-- Name: run_sock_evaluation_trigger(); Type: FUNCTION; Schema: zokni; Owner: -
--

CREATE FUNCTION zokni.run_sock_evaluation_trigger() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    BEGIN
        -- Csak akkor fusson, ha van session_data a sorban
        IF NEW.session_data IS NOT NULL THEN
            PERFORM zokni.evaluate_sock_biomechanics(NEW.session_id);
        END IF;
    EXCEPTION WHEN OTHERS THEN
        RAISE WARNING 'Zokni feldolgozási hiba (session_id: %): %', NEW.session_id, SQLERRM;
    END;

    RETURN NEW;
END;
$$;


--
-- Name: store_trunk_angle_series(text); Type: FUNCTION; Schema: zokni; Owner: -
--

CREATE FUNCTION zokni.store_trunk_angle_series(p_session_id text) RETURNS void
    LANGUAGE plpgsql
    AS $$
BEGIN
    UPDATE zokni.biomechanics_results AS b
    SET torzsszog_idosor = e.torzsszog_idosor
    FROM public.evaluate_biomechanics(p_session_id) AS e
    WHERE b.session_id = p_session_id
      AND e.session_id = b.session_id;
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
    IF NEW.analysis_status = 'inserted'
        AND (OLD.analysis_status IS DISTINCT FROM 'inserted') THEN
        PERFORM zokni.evaluate_and_store_biomechanics(NEW.session_id);
        PERFORM zokni.store_trunk_angle_series(NEW.session_id);
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: update_all_stability_scores(); Type: PROCEDURE; Schema: zokni; Owner: -
--

CREATE PROCEDURE zokni.update_all_stability_scores()
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_sd_low numeric;
    v_sd_high numeric;
    v_path_low numeric;
    v_path_high numeric;
    v_range_low numeric;
    v_range_high numeric;
BEGIN
    -- 1. Referenciaértékek (5. és 95. percentilis) kiszámítása a meglévő adatokból
    WITH metric_values AS (
        SELECT
            session_id,
            ((left_metrics->>'resultant_sd')::numeric + (right_metrics->>'resultant_sd')::numeric) / 2.0 AS sd_val,
            ((left_metrics->>'path_length')::numeric + (right_metrics->>'path_length')::numeric) / 2.0 AS path_val,
            ((left_metrics->>'range_resultant')::numeric + (right_metrics->>'range_resultant')::numeric) / 2.0 AS range_val
        FROM zokni.sock_analysis_results
        WHERE left_metrics IS NOT NULL AND right_metrics IS NOT NULL
    )
    SELECT
                PERCENTILE_CONT(0.05) WITHIN GROUP (ORDER BY sd_val),
                PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY sd_val),
                PERCENTILE_CONT(0.05) WITHIN GROUP (ORDER BY path_val),
                PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY path_val),
                PERCENTILE_CONT(0.05) WITHIN GROUP (ORDER BY range_val),
                PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY range_val)
    INTO
        v_sd_low, v_sd_high,
        v_path_low, v_path_high,
        v_range_low, v_range_high
    FROM metric_values;

    -- Ha nincs elegendő adat, kilépünk, hogy ne legyen hiba
    IF v_sd_low IS NULL THEN
        RAISE WARNING 'Nincs elegendő kiértékelt adat a referenciaértékek kiszámításához.';
        RETURN;
    END IF;

    -- Osztás nullával hiba elkerülése, ha minden mérés hajszálpontosan azonos lenne
    IF v_sd_high <= v_sd_low THEN v_sd_high := v_sd_low + 0.000000001; END IF;
    IF v_path_high <= v_path_low THEN v_path_high := v_path_low + 0.000000001; END IF;
    IF v_range_high <= v_range_low THEN v_range_high := v_range_low + 0.000000001; END IF;

    -- 2. Minden sor frissítése az inverz pontszámítással (klippelve 1.0 és 10.0 közé)
    WITH raw_metrics AS (
        SELECT
            session_id,
            ((left_metrics->>'resultant_sd')::numeric + (right_metrics->>'resultant_sd')::numeric) / 2.0 AS sd_val,
            ((left_metrics->>'path_length')::numeric + (right_metrics->>'path_length')::numeric) / 2.0 AS path_val,
            ((left_metrics->>'range_resultant')::numeric + (right_metrics->>'range_resultant')::numeric) / 2.0 AS range_val
        FROM zokni.sock_analysis_results
        WHERE left_metrics IS NOT NULL AND right_metrics IS NOT NULL
    ),
         calculated_scores AS (
             SELECT
                 session_id,
                 GREATEST(1.0, LEAST(10.0, 1.0 + (10.0 - 1.0) * (v_sd_high - sd_val) / (v_sd_high - v_sd_low))) AS sd_score,
                 GREATEST(1.0, LEAST(10.0, 1.0 + (10.0 - 1.0) * (v_path_high - path_val) / (v_path_high - v_path_low))) AS path_score,
                 GREATEST(1.0, LEAST(10.0, 1.0 + (10.0 - 1.0) * (v_range_high - range_val) / (v_range_high - v_range_low))) AS range_score
             FROM raw_metrics
         ),
         final_scores AS (
             SELECT
                 session_id,
                 -- A súlyozás: 40% SD, 40% Path length, 20% Range
                 ROUND((0.40 * sd_score + 0.40 * path_score + 0.20 * range_score), 1) AS total_score
             FROM calculated_scores
         )
    UPDATE zokni.sock_analysis_results t
    SET
        stability_score = f.total_score,
        stability_category = CASE
                                 WHEN f.total_score >= 8.5 THEN 'Nagyon stabil'
                                 WHEN f.total_score >= 7.0 THEN 'Stabil'
                                 WHEN f.total_score >= 5.5 THEN 'Közepes stabilitás'
                                 WHEN f.total_score >= 4.0 THEN 'Fokozott kilengés'
                                 ELSE 'Nagy kilengés'
            END
    FROM final_scores f
    WHERE t.session_id = f.session_id;

END;
$$;


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
    kiszamitva timestamp with time zone DEFAULT now(),
    torzsszog_idosor jsonb
);


--
-- Name: biomechanics_results; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.biomechanics_results WITH (security_invoker='true') AS
 SELECT session_id,
    osszpontszam,
    mean_velocity,
    sway_path,
    sway_area,
    torzs_ingadozas_sd,
    stabilitas_pont,
    alatamasztas_pont,
    szimmetria_pont,
    terhelesi_irany,
    ertekeles,
    kiszamitva,
    torzsszog_idosor
   FROM zokni.biomechanics_results;


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
-- Name: combined_analysis_results; Type: TABLE; Schema: zokni; Owner: -
--

CREATE TABLE zokni.combined_analysis_results (
    session_id text NOT NULL,
    video_trunk_sd numeric,
    sock_resultant_sd numeric,
    compensation_status text,
    insight_hu text,
    calculated_at timestamp with time zone DEFAULT now()
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
-- Name: combined_analysis_results combined_analysis_results_pkey; Type: CONSTRAINT; Schema: zokni; Owner: -
--

ALTER TABLE ONLY zokni.combined_analysis_results
    ADD CONSTRAINT combined_analysis_results_pkey PRIMARY KEY (session_id);


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
-- Name: sock_analysis_results sock_analysis_results_pkey; Type: CONSTRAINT; Schema: zokni; Owner: -
--

ALTER TABLE ONLY zokni.sock_analysis_results
    ADD CONSTRAINT sock_analysis_results_pkey PRIMARY KEY (session_id);


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
-- Name: sessions trg_auto_evaluate_sock; Type: TRIGGER; Schema: zokni; Owner: -
--

CREATE TRIGGER trg_auto_evaluate_sock AFTER INSERT OR UPDATE OF session_data ON zokni.sessions FOR EACH ROW EXECUTE FUNCTION zokni.auto_evaluate_sock();


--
-- Name: sessions trg_calc_biomechanics; Type: TRIGGER; Schema: zokni; Owner: -
--

CREATE TRIGGER trg_calc_biomechanics AFTER UPDATE OF analysis_status ON zokni.sessions FOR EACH ROW EXECUTE FUNCTION zokni.trigger_calc_biomechanics();


--
-- Name: sock_analysis_results trigger_combined_on_sock; Type: TRIGGER; Schema: zokni; Owner: -
--

CREATE TRIGGER trigger_combined_on_sock AFTER INSERT OR UPDATE ON zokni.sock_analysis_results FOR EACH ROW EXECUTE FUNCTION zokni.run_combined_trigger();


--
-- Name: biomechanics_results trigger_combined_on_video; Type: TRIGGER; Schema: zokni; Owner: -
--

CREATE TRIGGER trigger_combined_on_video AFTER INSERT OR UPDATE ON zokni.biomechanics_results FOR EACH ROW EXECUTE FUNCTION zokni.run_combined_trigger();


--
-- Name: sessions trigger_evaluate_sock_on_inserted; Type: TRIGGER; Schema: zokni; Owner: -
--

CREATE TRIGGER trigger_evaluate_sock_on_inserted AFTER INSERT OR UPDATE OF analysis_status ON zokni.sessions FOR EACH ROW WHEN ((new.analysis_status = 'inserted'::text)) EXECUTE FUNCTION zokni.run_sock_evaluation_trigger();


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
-- Name: biomechanics_results Saját eredmények megtekintése; Type: POLICY; Schema: zokni; Owner: -
--

CREATE POLICY "Saját eredmények megtekintése" ON zokni.biomechanics_results FOR SELECT TO authenticated USING ((EXISTS ( SELECT 1
   FROM zokni.sessions s
  WHERE ((s.session_id = biomechanics_results.session_id) AND (s.patient_id = (auth.uid())::text)))));


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
-- Name: biomechanics_results; Type: ROW SECURITY; Schema: zokni; Owner: -
--

ALTER TABLE zokni.biomechanics_results ENABLE ROW LEVEL SECURITY;

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

\unrestrict F2qwKCtXiEotdP3LQmtNoYqmRJczrqGqMAhIfIVgiQ5hFfSQ6gjvjbg4ALfmG3Z

