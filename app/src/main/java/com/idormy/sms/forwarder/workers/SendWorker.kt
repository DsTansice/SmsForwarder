package com.idormy.sms.forwarder.workers

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.gson.Gson
import com.idormy.sms.forwarder.core.Core
import com.idormy.sms.forwarder.database.entity.Logs
import com.idormy.sms.forwarder.database.entity.Msg
import com.idormy.sms.forwarder.database.entity.Rule
import com.idormy.sms.forwarder.entity.MsgInfo
import com.idormy.sms.forwarder.utils.*
import com.xuexiang.xutil.security.CipherUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.*

class SendWorker(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    @SuppressLint("SimpleDateFormat")
    override suspend fun doWork(): Result {

        return withContext(Dispatchers.IO) {
            try {
                // 免打扰(禁用转发)时间段
                if (SettingUtils.silentPeriodStart != SettingUtils.silentPeriodEnd) {
                    val periodStartDay = Date()
                    var periodStartEnd = Date()
                    //跨天了
                    if (SettingUtils.silentPeriodStart > SettingUtils.silentPeriodEnd) {
                        val c: Calendar = Calendar.getInstance()
                        c.time = periodStartEnd
                        c.add(Calendar.DAY_OF_MONTH, 1)
                        periodStartEnd = c.time
                    }

                    val dateFmt = SimpleDateFormat("yyyy-MM-dd")
                    val mTimeOption = DataProvider.timePeriodOption
                    val periodStartStr = dateFmt.format(periodStartDay) + " " + mTimeOption[SettingUtils.silentPeriodStart] + ":00"
                    val periodEndStr = dateFmt.format(periodStartEnd) + " " + mTimeOption[SettingUtils.silentPeriodEnd] + ":00"

                    val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    val periodStart = timeFmt.parse(periodStartStr, ParsePosition(0))?.time
                    val periodEnd = timeFmt.parse(periodEndStr, ParsePosition(0))?.time

                    val now = System.currentTimeMillis()
                    if (periodStart != null && periodEnd != null && now in periodStart..periodEnd) {
                        Log.e("SendWorker", "免打扰(禁用转发)时间段")
                        return@withContext Result.failure(workDataOf("send" to "failed"))
                    }
                }

                val msgInfoJson = inputData.getString(Worker.sendMsgInfo)
                val msgInfo = Gson().fromJson(msgInfoJson, MsgInfo::class.java)

                // 过滤重复消息机制
                if (SettingUtils.duplicateMessagesLimits > 0) {
                    val key = CipherUtils.md5(msgInfo.type + msgInfo.from + msgInfo.content)
                    val timestamp: Long = System.currentTimeMillis() / 1000L
                    var timestampPrev: Long by HistoryUtils(key, timestamp)
                    if (timestampPrev != timestamp && timestamp - timestampPrev <= SettingUtils.duplicateMessagesLimits) {
                        Log.e("SendWorker", "过滤重复消息机制")
                        return@withContext Result.failure(workDataOf("send" to "failed"))
                    }
                    timestampPrev = timestamp
                }

                //【注意】卡槽id：-1=获取失败、0=卡槽1、1=卡槽2，但是 Rule 表里存的是 SIM1/SIM2
                val simSlot = "SIM" + (msgInfo.simSlot + 1)
                val ruleList: List<Rule> = Core.rule.getRuleList(msgInfo.type, 1, simSlot)
                if (ruleList.isEmpty()) {
                    return@withContext Result.failure(workDataOf("send" to "failed"))
                }

                val msg = Msg(0, msgInfo.type, msgInfo.from, msgInfo.content, msgInfo.simSlot, msgInfo.simInfo, msgInfo.subId)
                val msgId = Core.msg.insert(msg)

                for (rule in ruleList) {
                    Log.d("SendWorker", rule.toString())
                    if (!rule.checkMsg(msgInfo)) continue

                    val sender = rule.senderList[0]
                    val log = Logs(0, msgInfo.type, msgId, rule.id, sender.id)
                    val logId = Core.logs.insert(log)
                    SendUtils.sendMsgSender(msgInfo, rule, 0, logId, msgId)
                }

            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext Result.failure(workDataOf("send" to e.message.toString()))
            }

            return@withContext Result.success()
        }
    }

}