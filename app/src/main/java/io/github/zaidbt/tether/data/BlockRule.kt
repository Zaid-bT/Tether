/*
Purpose:
This file defines one saved rule: "this app has this daily limit".
It's the shape of a row in the database, and everything that saves or reads rules passes BlockRule objects around
*/

//Room is Android's official library for storing structured data in a local database on the phone.
//It sits on top of SQLite (the database built into Android)

package io.github.zaidbt.tether.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// One saved rule per app; limitMinutes = 0 means "always blocked"
@Entity(tableName = "block_rules")
data class BlockRule(
    @PrimaryKey val packageName: String, //apps unique package id
    val limitMinutes: Int = 0
)

/*
table block_rules
  packageName   TEXT     NOT NULL   PRIMARY KEY
  limitMinutes  INTEGER  NOT NULL
*/
