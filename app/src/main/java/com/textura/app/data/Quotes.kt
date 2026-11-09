package com.textura.app.data

import java.time.LocalDate

data class Quote(val text: String, val author: String) {
    /** Text ready to drop into the text box. */
    fun asText(): String = "“$text”\n— $author"
}

/** Short public-domain quotes and proverbs for the "quote of the day" and the widget. */
object Quotes {
    val all: List<Quote> = listOf(
        Quote("The journey of a thousand miles begins with a single step.", "Lao Tzu"),
        Quote("Fortune favours the bold.", "Virgil"),
        Quote("The only thing we have to fear is fear itself.", "Franklin D. Roosevelt"),
        Quote("All the world’s a stage, and all the men and women merely players.", "William Shakespeare"),
        Quote("Brevity is the soul of wit.", "William Shakespeare"),
        Quote("I think, therefore I am.", "René Descartes"),
        Quote("The unexamined life is not worth living.", "Socrates"),
        Quote("No man is an island.", "John Donne"),
        Quote("Hope is the thing with feathers that perches in the soul.", "Emily Dickinson"),
        Quote("Do what you can, with what you have, where you are.", "Theodore Roosevelt"),
        Quote("That which does not kill us makes us stronger.", "Friedrich Nietzsche"),
        Quote("He who has a why to live can bear almost any how.", "Friedrich Nietzsche"),
        Quote("Carpe diem.", "Horace"),
        Quote("Genius is one percent inspiration and ninety-nine percent perspiration.", "Thomas Edison"),
        Quote("Always do right. This will gratify some people and astonish the rest.", "Mark Twain"),
        Quote("Knowledge is power.", "Francis Bacon"),
        Quote("The pen is mightier than the sword.", "Edward Bulwer-Lytton"),
        Quote("No act of kindness, no matter how small, is ever wasted.", "Aesop"),
        Quote("Slow and steady wins the race.", "Aesop"),
        Quote("A person is a person through other people.", "Zulu proverb"),
        Quote("If you want to go fast, go alone. If you want to go far, go together.", "African proverb"),
        Quote("The best time to plant a tree was twenty years ago. The second best time is now.", "Proverb"),
        Quote("Fall seven times, stand up eight.", "Japanese proverb"),
        Quote("A smooth sea never made a skilled sailor.", "Proverb"),
        Quote("Where there’s a will, there’s a way.", "Proverb"),
        Quote("Actions speak louder than words.", "Proverb"),
        Quote("Every cloud has a silver lining.", "Proverb"),
        Quote("Rome wasn’t built in a day.", "Proverb"),
        Quote("Still waters run deep.", "Proverb"),
        Quote("Practice makes perfect.", "Proverb"),
        Quote("A friend in need is a friend indeed.", "Proverb"),
        Quote("Time and tide wait for no man.", "Proverb"),
    )

    fun ofTheDay(): Quote = all[(LocalDate.now().dayOfYear + LocalDate.now().year) % all.size]

    fun random(except: String? = null): Quote = all.filter { it.text != except }.random()
}
