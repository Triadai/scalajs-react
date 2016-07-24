package japgolly.scalajs.react

import scalajs.js
import utest._
import japgolly.scalajs.react.test.ReactTestUtils
import japgolly.scalajs.react.internal.JsUtil.inspectObject
import japgolly.scalajs.react.test.TestUtil._
import scalaz.Equal

object ScalaComponentPTest extends TestSuite {

  case class BasicProps(name: String)

  val BasicComponent =
    ScalaComponent.build[BasicProps]("HelloMessage")
      .stateless
      .noBackend
      .render_P(p => raw.React.createElement("div", null, "Hello ", p.name))
      .build

  override def tests = TestSuite {

    'basic {
      val unmounted = BasicComponent(BasicProps("Bob"))
      assertEq(unmounted.props.name, "Bob")
      assertEq(unmounted.propsChildren.count, 0)
      assertEq(unmounted.propsChildren.isEmpty, true)
      assertEq(unmounted.key, None)
      assertEq(unmounted.ref, None)
      withBodyContainer { mountNode =>
        val mounted = unmounted.renderIntoDOM(mountNode)
        val n = mounted.getDOMNode
        assertOuterHTML(n, "<div>Hello Bob</div>")
        assertEq(mounted.isMounted, true)
        assertEq(mounted.props.name, "Bob")
        assertEq(mounted.propsChildren.count, 0)
        assertEq(mounted.propsChildren.isEmpty, true)
        assertEq(mounted.state, ())
        assertEq(mounted.backend, ())
      }
    }

    'ctorReuse -
      assert(BasicComponent(BasicProps("a")) ne BasicComponent(BasicProps("b")))

    'lifecycle {

      case class Props(a: Int, b: Int, c: Int) {
        def -(x: Props) = Props(
          this.a - x.a,
          this.b - x.b,
          this.c - x.c)
      }
      implicit def equalProps = Equal.equalA[Props]

      var mountCountA = 0
      var mountCountB = 0
      var mountCountBeforeMountA = 0
      var mountCountBeforeMountB = 0
      var willMountCountA = 0
      var willMountCountB = 0

      def assertMountCount(expect: Int): Unit = {
        assertEq("mountCountA", mountCountA, expect)
        assertEq("mountCountB", mountCountB, expect)
        assertEq("willMountCountA", willMountCountA, expect)
        assertEq("willMountCountB", willMountCountB, expect)
        assertEq("mountCountBeforeMountA", mountCountBeforeMountA, 0)
        assertEq("mountCountBeforeMountB", mountCountBeforeMountB, 0)
      }

      var didUpdates = Vector.empty[Props]
      var willUpdates = Vector.empty[Props]
      def assertUpdates(ps: Props*): Unit = {
        val e = ps.toVector
        assertEq("willUpdates", willUpdates, e)
        assertEq("didUpdates", didUpdates, e)
      }

      var recievedPropDeltas = Vector.empty[Props]

      var willUnmountCount = 0

      class Backend($: BackendScope[Props, Unit]) {
        def willMount = Callback { mountCountBeforeMountB += mountCountB; willMountCountB += 1 }
        def incMountCount = Callback(mountCountB += 1)
        def willUpdate(cur: Props, next: Props) = Callback(willUpdates :+= next - cur)
        def didUpdate(prev: Props, cur: Props) = Callback(didUpdates :+= cur - prev)
        def receive(cur: Props, next: Props) = Callback(recievedPropDeltas :+= next - cur)
        def incUnmountCount = Callback(willUnmountCount += 1)
      }

      val Comp = ScalaComponent.build[Props]("")
        .stateless
        .backend(new Backend(_))
        .render_P(p => raw.React.createElement("div", null, s"${p.a} ${p.b} ${p.c}"))
        .shouldComponentUpdate(_.cmpProps(_.a != _.a)) // update if .a differs
        .shouldComponentUpdate(_.cmpProps(_.b != _.b)) // update if .b differs
        .componentDidMount(_ => Callback(mountCountA += 1))
        .componentDidMount(_.backend.incMountCount)
        .componentWillMount(_ => Callback { mountCountBeforeMountA += mountCountA; willMountCountA += 1 })
        .componentWillMount(_.backend.willMount)
        .componentWillUpdate(x => x.backend.willUpdate(x.currentProps, x.nextProps))
        .componentDidUpdate(x => x.backend.didUpdate(x.prevProps, x.currentProps))
        .componentWillUnmount(_.backend.incUnmountCount)
        .componentWillReceiveProps(x => x.backend.receive(x.currentProps, x.nextProps))
        .build

      withBodyContainer { mountNode =>
        assertMountCount(0)

        var mounted = Comp(Props(1, 2, 3)).renderIntoDOM(mountNode)
        assertMountCount(1)
        assertOuterHTML(mounted.getDOMNode, "<div>1 2 3</div>")
        assertUpdates()

        mounted = Comp(Props(1, 2, 8)).renderIntoDOM(mountNode)
        assertOuterHTML(mounted.getDOMNode, "<div>1 2 3</div>")
        assertUpdates()

        mounted = Comp(Props(1, 5, 8)).renderIntoDOM(mountNode)
        assertOuterHTML(mounted.getDOMNode, "<div>1 5 8</div>")
        assertUpdates(Props(0, 3, 0))

        assertEq("willUnmountCount", willUnmountCount, 0)
      }

      assertMountCount(1)
      assertEq("willUnmountCount", willUnmountCount, 1)
      assertEq("recievedPropDeltas", recievedPropDeltas, Vector(Props(0, 0, 5), Props(0, 3, 0)))
    }
  }
}


object ScalaComponentSTest extends TestSuite {

  case class State(num1: Int, s2: State2)
  case class State2(num2: Int, num3: Int)

  implicit val equalState: Equal[State] = Equal.equalA
  implicit val equalState2: Equal[State2] = Equal.equalA

  class Backend($: BackendScope[Unit, State]) {
    val inc: Callback =
      $.modState(s => s.copy(s.num1 + 1))
  }

  val Component =
    ScalaComponent.build[Unit]("State, no Props")
      .initialState(State(123, State2(400, 7)))
      .backend(new Backend(_))
      .render_S(s => raw.React.createElement("div", null, "State = ", s.num1, " + ", s.s2.num2, " + ", s.s2.num3))
      .build

  override def tests = TestSuite {

    'main {
      val unmounted = Component()
      assert(unmounted.propsChildren.isEmpty)
      assertEq(unmounted.key, None)
      assertEq(unmounted.ref, None)
      withBodyContainer { mountNode =>
        val mounted = unmounted.renderIntoDOM(mountNode)
        val n = mounted.getDOMNode

        assertOuterHTML(n, "<div>State = 123 + 400 + 7</div>")
        assertEq(mounted.isMounted, true)
        assertEq(mounted.propsChildren.count, 0)
        assertEq(mounted.propsChildren.isEmpty, true)
        assertEq(mounted.state, State(123, State2(400, 7)))
        val b = mounted.backend

        mounted.setState(State(666, State2(500, 7)))
        assertOuterHTML(n, "<div>State = 666 + 500 + 7</div>")
        assertEq(mounted.isMounted, true)
        assertEq(mounted.propsChildren.isEmpty, true)
        assertEq(mounted.state, State(666, State2(500, 7)))
        assert(mounted.backend eq b)

        mounted.backend.inc.runNow()
        assertOuterHTML(n, "<div>State = 667 + 500 + 7</div>")
        assertEq(mounted.isMounted, true)
        assertEq(mounted.propsChildren.isEmpty, true)
        assertEq(mounted.state, State(667, State2(500, 7)))
        assert(mounted.backend eq b)

        val zoomed = mounted
          .zoomState(_.s2)(n => _.copy(s2 = n))
          .zoomState(_.num2)(n => _.copy(num2 = n))
        assertEq(zoomed.state, 500)
        zoomed.modState(_ + 1)
        assertOuterHTML(n, "<div>State = 667 + 501 + 7</div>")
        assertEq(mounted.isMounted, true)
        assertEq(mounted.propsChildren.isEmpty, true)
        assertEq(mounted.state, State(667, State2(501, 7)))
        assert(mounted.backend eq b)
      }
    }

    'ctorReuse -
      assert(Component() eq Component())

  }
}