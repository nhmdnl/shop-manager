package com.archsolutions.shopmanager.data.db.converters

import androidx.room.TypeConverter
import com.archsolutions.shopmanager.data.db.entities.CountPhase
import com.archsolutions.shopmanager.data.db.entities.DayStatus
import com.archsolutions.shopmanager.data.db.entities.MoneyEntryType
import com.archsolutions.shopmanager.data.db.entities.MoneyMethod
import com.archsolutions.shopmanager.data.db.entities.MovementType
import com.archsolutions.shopmanager.data.db.entities.PayMethod
import com.archsolutions.shopmanager.data.db.entities.PurchasePaid
import com.archsolutions.shopmanager.data.db.entities.SaleStatus
import com.archsolutions.shopmanager.data.db.entities.Unit

class Converters {
    @TypeConverter
    fun fromUnit(value: Unit?): String? = value?.name

    @TypeConverter
    fun toUnit(value: String?): Unit? = value?.let { Unit.valueOf(it) }

    @TypeConverter
    fun fromPayMethod(value: PayMethod?): String? = value?.name

    @TypeConverter
    fun toPayMethod(value: String?): PayMethod? = value?.let { PayMethod.valueOf(it) }

    @TypeConverter
    fun fromMoneyMethod(value: MoneyMethod?): String? = value?.name

    @TypeConverter
    fun toMoneyMethod(value: String?): MoneyMethod? = value?.let { MoneyMethod.valueOf(it) }

    @TypeConverter
    fun fromSaleStatus(value: SaleStatus?): String? = value?.name

    @TypeConverter
    fun toSaleStatus(value: String?): SaleStatus? = value?.let { SaleStatus.valueOf(it) }

    @TypeConverter
    fun fromMovementType(value: MovementType?): String? = value?.name

    @TypeConverter
    fun toMovementType(value: String?): MovementType? = value?.let { MovementType.valueOf(it) }

    @TypeConverter
    fun fromMoneyEntryType(value: MoneyEntryType?): String? = value?.name

    @TypeConverter
    fun toMoneyEntryType(value: String?): MoneyEntryType? = value?.let { MoneyEntryType.valueOf(it) }

    @TypeConverter
    fun fromPurchasePaid(value: PurchasePaid?): String? = value?.name

    @TypeConverter
    fun toPurchasePaid(value: String?): PurchasePaid? = value?.let { PurchasePaid.valueOf(it) }

    @TypeConverter
    fun fromDayStatus(value: DayStatus?): String? = value?.name

    @TypeConverter
    fun toDayStatus(value: String?): DayStatus? = value?.let { DayStatus.valueOf(it) }

    @TypeConverter
    fun fromCountPhase(value: CountPhase?): String? = value?.name

    @TypeConverter
    fun toCountPhase(value: String?): CountPhase? = value?.let { CountPhase.valueOf(it) }
}
