package com.twitter.finagle.tunable

import com.twitter.util.tunable.{JsonTunableMapper, NullTunableMap, ServiceLoadedTunableMap,
  TunableMap}
import com.twitter.finagle.server.ServerInfo

/**
 * Object used for getting the [[TunableMap]] for a given `id`. This [[TunableMap]] is composed
 * from 3 sources, in order of priority:
 *
 *  i. A mutable, in-process [[TunableMap.Mutable]].
 *  i. The dynamically loaded [[TunableMap]], provided via [[ServiceLoadedTunableMap.apply]].
 *  i. The JSON file-based [[TunableMap]], provided via [[JsonTunableMapper.loadJsonTunables]].
 *
 *  The JSON file-based [[TunableMap]] is a composition of file-based per-instance and
 *  per-environment [[TunableMap]]s. [[TunableMap]]s are composed in the following priority order:
 *
 *  i.  Environment and instance-specific
 *  i.  Environment-specific for all instances
 *  i.  Instance-specific
 *  i.  All instances
 *
 *  A new composed [[TunableMap]] is returned on each `apply`.
 */
private[twitter] object StandardTunableMap {

  def apply(id: String): TunableMap =
    apply(id, ServerInfo(), TunableMap.newMutable())

  // Exposed for testing
  private[tunable] def apply(
    id: String,
    serverInfo: ServerInfo,
    mutable: TunableMap
  ): TunableMap = {
    val json = loadJsonConfig(id, serverInfo)
    TunableMap.of(
      mutable,
      ServiceLoadedTunableMap(id),
      json
    )
  }

  /**
   * Load [[TunableMap]]s from JSON configuration files. We look for the following files in the
   * com/twitter/tunables/`id` directory and compose the resulting [[TunableMap]]s in order of
   * priority, where $env is serverInfo.environment` (if it exists) and $id is
   * `serverInfo.id`:
   *
   * i. $env/instance-$id.json
   * i. $env/instances.json
   * i. instance-$id.json
   * i. instances.json
   */
  private[tunable] def loadJsonConfig(id: String, serverInfo: ServerInfo): TunableMap = {
    val instanceId = serverInfo.id

    val pathTemplate = s"com/twitter/tunables/$id/%sinstance%s.json"

    val envPathParams = serverInfo.environment match {
      case Some(env) => Seq(Seq(s"$env/", s"-$instanceId"), Seq(s"$env/", "s"))
      case None => Seq.empty[Seq[String]]
    }

    val instancePathParams = Seq(Seq("", s"-$instanceId"), Seq("", "s"))

    val pathParams = envPathParams ++ instancePathParams

    var tunableMap: TunableMap = NullTunableMap
    pathParams.foreach { params =>
      val path = pathTemplate.format(params: _*)
      tunableMap = tunableMap.orElse(JsonTunableMapper().loadJsonTunables(id, path))
    }
    tunableMap
  }
}