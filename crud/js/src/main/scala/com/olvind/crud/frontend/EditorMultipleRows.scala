package com.olvind.crud
package frontend

import autowire._
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra._
import japgolly.scalajs.react.vdom.prefix_<^._
import org.scalajs.dom

import scala.scalajs.js
import scalacss.ScalaCssReact._

object EditorMultipleRows
  extends EditorBaseMultipleRows
  with EditorBaseUpdaterPrimary {

  case class Props (
    base: EditorBaseProps
  ) extends PropsB

  case class State(
    params:      QueryParams,
    data:        DataState,
    cachedDataU: U[CachedData],
    isUpdating:  Boolean,
    isAtBottom:  Boolean) extends StateBP[State]{

    override def withDataState(data: DataState) =
      copy(data = data)

    override def withCachedData(cd: CachedData) =
      copy(cachedDataU = cd)
  }

  final case class Backend($: WrapBackendScope[Props, State])
    extends BackendBUP[Props, State]
    with OnUnmount {

    val dialogCtl = new FilteringDialog.DialogController

    override def handleDeleted(id: StrRowId): Callback =
      $.state.map(_.data).flatMap{
        case HasDataState(rows) ⇒
          setData(HasDataState(rows.filterNot(_.idOpt =:= id.some)), Callback.empty)
        case _ ⇒ Callback.empty
      }

    override def patchRow(id: StrRowId, row: StrTableRow): Callback =
      $.state.map(_.data) flatMap {
        case HasDataState(rows) ⇒
          val StableId = id.some
          val newRows = rows map {
            case StrTableRow(StableId, _) ⇒ row
            case otherRow                 ⇒ otherRow
          }
          setData(HasDataState(newRows), Callback.empty)
        case _ ⇒ Callback.empty
      }

    override def loadInitialData: Callback =
      $.state.flatMap(S ⇒ fetchRows(S.data, S.params.copy(page = PageNum.zero), append = false))

    def fetchRows(dataState: DataState, params: QueryParams, append: Boolean): Callback =
      $.modState(_.copy(isUpdating = true)) >>
      asyncCb("Couldn't read rows", remote.read($.props.base.userInfo, params.some).call())
        .commit {
          read ⇒
            val newData = (dataState, append) match {
              case (HasDataState(existing), true) ⇒ HasDataState(existing ++ read.rows)
              case _                              ⇒ HasDataState(read.rows)
            }
            setData(newData, $.modState(_.copy(params = params, isUpdating = false)))
        }

    def onFilteringChanged(S: State): Option[Filter] ⇒ Callback =
      of ⇒ fetchRows(S.data, S.params.withFilter(of), append = false)

    def onSort(S: State): ColumnInfo ⇒ Callback =
      c ⇒ fetchRows(S.data, S.params.withSortedBy(c), append = false)

    val onScroll: dom.UIEvent ⇒ Callback = {
      e ⇒
        val w          = e.currentTarget.asInstanceOf[dom.Window]
        val d          = e.target.asInstanceOf[dom.Document]
        val scrollY    = w.asInstanceOf[js.Dynamic].scrollY.asInstanceOf[Double]
        val y          = d.documentElement.scrollHeight - w.innerHeight
        val isAtBottom = scrollY / y > 0.98

        $.state.flatMap{
          S ⇒
            $.modState(
              _.copy(isAtBottom = isAtBottom),
              fetchRows(S.data, S.params.withNextPage, append = true)
            ).filter(isAtBottom != S.isAtBottom && !S.isUpdating)
        }
    }

    override def renderData(S: State, table: ClientTable, rows: Seq[StrTableRow]): ReactElement = {
      <.div(
        TableStyle.container,
        FilteringDialog()(FilteringDialog.Props(
          dialogCtl,
          $.props.table.columns,
          S.params.filter,
          onFilteringChanged(S),
          S.cachedDataU
        )),
        EditorToolbar()(EditorToolbar.Props(
          table             = $.props.table,
          rows              = rows.size,
          cachedDataU       = S.cachedDataU,
          filterU           = S.params.filter.asUndef,
          openFilterDialogU = dialogCtl.openDialog,
          isLinkedU         = uNone,
          refreshU          = reInit,
          showAllU          = uNone,
          deleteU           = uNone,
          showCreateU       = (false, $.props.base.ctl.setEH(RouteCreateRow($.props.table))),
          customElemU       = uNone
        )),
        <.div(
          TableStyle.table,
          TableHeader()(TableHeader.Props(
            table,
            S.params.sorting.asUndef,
            onSort(S)
          )),
          rows.map(
            r ⇒ TableRow(r)(TableRow.Props(
              table,
              r,
              S.cachedDataU,
              r.idOpt.asUndef.map(updateValue),
              showSingleRow
            ))
          ),
          S.isUpdating ?= renderWaiting
        )
      )
    }
  }

  val component = ReactComponentB[Props]("EditorMultipleRows")
    .initialState_P(P ⇒
      State(
        QueryParams(QueryParams.defaultPageSize, PageNum.zero, sorting = None, filter = None),
        InitialState,
        P.base.cachedDataF.currentValueU,
        isUpdating = false,
        isAtBottom = false
      )
    )
    .backend($ ⇒ Backend(WrapBackendScope($)))
    .render($ ⇒ $.backend.render($.props, $.state))
    .configure(ComponentUpdates.inferred("EditorMultipleRows"))
    .configure(EventListener[dom.UIEvent].install("scroll", _.backend.onScroll, _ => dom.window))
    .componentDidMount(_.backend.init)
    .build

  def apply(table: ClientTable) =
    component.withKey(table.name.value)
}
