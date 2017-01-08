package domain

import java.time.LocalDateTime

/**
  * Created by alexandregenon on 07/01/17.
  */
case class Song(artist:String,
                song : String,
                start: LocalDateTime,
                end: LocalDateTime,
                imageURL: String
               )
