package cn.edu.bjfu.redislearn.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * @author chaos
 * @date 2021-12-13 22:44
 */
@Service
public class SignInService {

    private final RedisTemplate<String, String> redisTemplate;

    @Autowired
    public SignInService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 用户签到
     *
     * @param aid  用户ID
     * @param date 日期
     * @return 之前的签到状态
     */
    public boolean doSign(int aid, LocalDate date) {
        int offset = date.getDayOfMonth() - 1;
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setBit(buildSignKey(aid, date), offset, true));
    }


    /**
     * 检查用户是否签到
     *
     * @param aid  用户ID
     * @param date 日期
     * @return 当前的签到状态
     */
    public boolean checkSign(int aid, LocalDate date) {
        int offset = date.getDayOfMonth() - 1;
        return Boolean.TRUE.equals(redisTemplate.opsForValue().getBit(buildSignKey(aid, date), offset));
    }

    /**
     * 获取用户当月签到次数
     *
     * @param aid  用户ID
     * @param date 日期
     * @return 当月的签到次数
     */
    public int getSignCount(int aid, LocalDate date) {
        try {
            return redisTemplate.execute(
                    ((RedisCallback<Integer>) connection -> Math.toIntExact(connection.bitCount(buildSignKey(aid, date).getBytes())))
            );
        } catch (Exception e) {
            return 0;
        }
    }


    /**
     * 获取无限连续签到次数
     *
     * @param aid  用户ID
     * @param date 日期
     * @return 无限连续签到次数
     */
    public long getContinuousSignCount(int aid, LocalDate date) {
        int signCount = 0;
        List<Long> list = redisTemplate.execute(
                (RedisCallback<List<Long>>) connection -> connection.bitField(buildSignKey(aid, date).getBytes(),
                        BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(date.getDayOfMonth())).valueAt(0)));
        List<Long> list1 = redisTemplate.opsForValue().bitField(buildSignKey(aid, date),
                BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(date.getDayOfMonth())).valueAt(0));

        if (!CollectionUtils.isEmpty(list)) {
            // 取低位连续不为0的个数即为连续签到次数，需考虑当天尚未签到的情况
            long v = list.get(0) == null ? 0 : list.get(0);
            for (int i = 0; i < date.getDayOfMonth(); i++) {
                if (v >> 1 << 1 == v) {
                    // 低位为0且非当天说明连续签到中断了
                    if (i > 0) {
                        break;
                    }
                } else {
                    signCount += 1;
                }
                v >>= 1;
            }
        }

        /*int offset = -1;
        int count = 1;

        int daysOfMonth = getDaysOfMonth(DateUtil.offsetMonth(new Date(), offset));
        int days = date.getDayOfMonth() + daysOfMonth;


        if (signCount == date.getDayOfMonth()) {
            // 当代码中使用递归时碰到了想中途退出递归,但是代码继续执行的情况,抛出异常上层捕获,避免跳出递归获取的值不正确问题
            try {
                getSignCount(aid, signCount, offset, count, days);
            } catch (Exception e) {
                signCount = Integer.parseInt(e.getMessage());
            }
        }*/
        return signCount;
    }


    /*private int getSignCount(int aid, int signCount, int offset, int count, int days) throws Exception {
        // 上上个月
        DateTime dateTime1 = DateUtil.offsetMonth(new Date(), offset * count);


        // 获取上上个月的天数
        String lastDays = String.format("u%d", getDaysOfMonth(dateTime1));


        List<Long> lastList = jedis.bitfield(buildSignKey(aid, dateToLocalDate(dateTime1)), "GET", lastDays, "0");


        if (CollUtil.isNotEmpty(lastList)) {
            // 取低位连续不为0的个数即为连续签到次数，需考虑当天尚未签到的情况
            long v = lastList.get(0) == null ? 0 : lastList.get(0);


            for (int i = 0; i < getDaysOfMonth(dateTime1); i++) {
                if (v >> 1 << 1 == v) {
                    // 低位为0且非当天说明连续签到中断了
                    if (i > 0) {
                        break;
                    }
                } else {
                    signCount += 1;
                }
                v >>= 1;
            }
            count += 1;
        }
        // 如果连续签到次数小于了当前月天数+多个整月天数，证明连续签到中断
        if (signCount < days) {
            throw new Exception(String.valueOf(signCount));
        }


        // 当前月总的天数+上个月的天数
        days = days + getDaysOfMonth(DateUtil.offsetMonth(new Date(), offset * (count - 1)));
        getSignCount(aid, signCount, offset, count, days);


        return signCount;
    }*/


    /**
     * 获取当月首次签到日期
     *
     * @param aid  用户ID
     * @param date 日期
     * @return 首次签到日期
     */
    public LocalDate getFirstSignDate(int aid, LocalDate date) {
        long pos = redisTemplate.execute((RedisCallback<Long>) con -> con.bitPos(buildSignKey(aid, date).getBytes(), true));
        return pos < 0 ? null : date.withDayOfMonth((int) (pos + 1));
    }


    /**
     * 获取当月签到情况
     *
     * @param aid  用户ID
     * @param date 日期
     * @return Key为签到日期，Value为签到状态的Map
     */

    public Map<String, Boolean> getSignInfo(int aid, LocalDate date) {
        Map<String, Boolean> signMap = new HashMap<>(date.getDayOfMonth());
        List<Long> list = redisTemplate.execute(
                (RedisCallback<List<Long>>) connection -> connection.bitField(buildSignKey(aid, date).getBytes(),
                        BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(date.getDayOfMonth())).valueAt(0)));
        if (CollUtil.isNotEmpty(list)) {
            // 由低位到高位，为0表示未签，为1表示已签
            long v = list.get(0) == null ? 0 : list.get(0);
            for (int i = date.lengthOfMonth(); i > 0; i--) {
                LocalDate d = date.withDayOfMonth(i);
                signMap.put(formatDate(d, "yyyy-MM-dd"), v >> 1 << 1 != v);
                v >>= 1;
            }
        }
        return signMap;
    }


    /**
     * 构建指定类型的Redis的key：u:sign:10000:202001
     */
    private static String buildSignKey(int aid, LocalDate date) {
        return String.format("u:sign:%d:%s", aid, formatDate(date));
    }


    /**
     * 获取Date类型的当月的天数
     */
    private static int getDaysOfMonth(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        return calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
    }


    /**
     * 固定202001格式
     */
    private static String formatDate(LocalDate date) {
        return formatDate(date, "yyyyMM");
    }


    /**
     * LocalDate按照指定格式进行转换字符串
     */
    private static String formatDate(LocalDate date, String pattern) {
        return date.format(DateTimeFormatter.ofPattern(pattern));
    }


    /**
     * Date类型转换成LocalDate
     */
    private static LocalDate dateToLocalDate(Date date) {
        Instant instant = date.toInstant();
        ZoneId zoneId = ZoneId.systemDefault();
        LocalDateTime localDateTime = instant.atZone(zoneId).toLocalDateTime();


        return LocalDate.from(localDateTime);
    }

}






 
 
