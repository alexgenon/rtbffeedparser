package domain

import java.time.LocalDateTime

/**
  * Created by alexandregenon on 07/01/17.
  */
case class Song(
                 artist: String,
                 song: String,
                 start: LocalDateTime,
                 end: LocalDateTime,
                 imageURL: String
               ){
  def id = start
}

object Song{
  val exampleSong = Song("coucou","gamin",LocalDateTime.now(),LocalDateTime.now(),"http://img.clubic.com/01F4000008315174-photo-donald-trump.jpg")
}