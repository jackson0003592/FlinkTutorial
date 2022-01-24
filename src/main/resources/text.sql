-- 2021/09/28全天，请求成功、响应时长大于300ms、后端call后端、Top10接口超时情况统计
-- 结果列说明：
--url：接口地址
--max_RT：最大响应时长
--avg_RT：对应API调用中，RT超过阈值的请求的平均值
--RT_great_than_300ms_cnt：对应API调用中，RT超过阈值的次数
--cnt：API被调用的总次数
--ratio：API调用中RT超过阈值的次数与该API调用总次数的比率 (RT_great_than_300ms_cnt / cnt)
WITH
    tmp AS (
        SELECT
            pkid
             , traceid
             ,to_number(response_time, '999999999999999')/1000000 res_time
             , REGEXP_REPLACE(
                REGEXP_REPLACE(
                        content_para_rest_url
                    ,'[a-z0-9]{8}-[a-z0-9]{4}-[a-z0-9]{4}-[a-z0-9]{4}-[a-z0-9]{12}'
                    ,'{uuid}'
                    ,'g'
                    )
            ,'[0-9]{13,}'
            ,'{id}'
            ,'g'
            ) AS content_para_rest_url
             ,s_timestamp
        FROM trace.ods_prod_tracelog_rt
        WHERE ds = '20210928'
          AND trace_type = 'response'
          AND response_time IS NOT NULL
          AND response_time != ''
    AND content_result_retcode = '200'
    -- 后端
    and who_from1_agent = '' and who_from1_page = ''

    )

SELECT  url
     ,max_RT
     ,avg_RT
     ,rt_great_than_300ms_cnt
     ,cnt
     ,concat(round(cast(rt_great_than_300ms_cnt as numeric)/cnt * 100, 5),'%') ratio
FROM (
         (SELECT  content_para_rest_url as url
               ,round(MAX(res_time))   max_RT
               ,round(AVG(res_time))   avg_RT
               ,COUNT(*) rt_great_than_300ms_cnt
          FROM    tmp
          WHERE   res_time > 300
          GROUP BY content_para_rest_url) t1
             JOIN
             (SELECT  content_para_rest_url,COUNT(*) AS cnt
              FROM    tmp
              GROUP BY content_para_rest_url
             ) t2
             on t1.url = t2.content_para_rest_url
         ) t
ORDER BY rt_great_than_300ms_cnt DESC
    LIMIT 10
;
