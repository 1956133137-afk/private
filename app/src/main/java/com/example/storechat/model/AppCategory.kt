package com.example.storechat.model

enum class AppCategory(val title: String) {
    YANNUO("彦诺自研"),
    ICBC("工行系列"),
    CCB("建行系列"),
    SMART_TRAVEL("智慧通行"),
    E_CLASS_PLATE("电子班牌");

    val id: Int
        get() = when (this) {
            CCB -> 1
            ICBC -> 2
            SMART_TRAVEL -> 3
            E_CLASS_PLATE -> 4
            YANNUO -> 0
        }

    companion object {
        fun from(id: Int): AppCategory? = entries.find { it.id == id }
    }
}
