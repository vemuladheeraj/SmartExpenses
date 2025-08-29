package com.dheeraj.smartexpenses.sms

import android.content.Context

/** Simple holder to get app context without leaking Activity */
object SmartContextProvider {
    lateinit var app: Context
}
