package au.csiro.data61.dataFusion.db

object Main {
  def main(args: Array[String]): Unit = {
    // Generate Tables.scala. Args: slickProfile, jdbcDriver, url, outputFolder, pkg, user, password
    // e.g. slick.jdbd.PostgresProfile org.postgresql.Driver jdbc:postgresql:dbname generated au.csiro.data61.dataFusion.db user password
    slick.codegen.SourceCodeGenerator.main(args)
  }
}