// @GENERATOR:play-routes-compiler
// @SOURCE:D:/Doneload/5074 IT+_Project_Grop O_Draft/conf/routes
// @DATE:Wed Feb 11 18:06:59 GMT 2026


package router {
  object RoutesPrefix {
    private var _prefix: String = "/"
    def setPrefix(p: String): Unit = {
      _prefix = p
    }
    def prefix: String = _prefix
    val byNamePrefix: Function0[String] = { () => prefix }
  }
}
