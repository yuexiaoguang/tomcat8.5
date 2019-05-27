package org.apache.catalina.tribes.group.interceptors;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.catalina.tribes.ChannelException;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.group.AbsoluteOrder;
import org.apache.catalina.tribes.group.ChannelInterceptorBase;

/**
 * 一个小的协调器, 只使用成员数组的排序版本.
 */
public class SimpleCoordinator extends ChannelInterceptorBase {

    private Member[] view;

    private final AtomicBoolean membershipChanged = new AtomicBoolean();

    private void membershipChanged() {
        membershipChanged.set(true);
    }

    @Override
    public void memberAdded(final Member member) {
        super.memberAdded(member);
        membershipChanged();
        installViewWhenStable();
    }

    @Override
    public void memberDisappeared(final Member member) {
        super.memberDisappeared(member);
        membershipChanged();
        installViewWhenStable();
    }

    /**
     * 重写以接收视图更改.
     *
     * @param view 成员数组
     */
    protected void viewChange(final Member[] view) {
    }

    @Override
    public void start(int svc) throws ChannelException {
        super.start(svc);
        installViewWhenStable();
    }

    private void installViewWhenStable() {
        int stableCount = 0;

        while (stableCount < 10) {
            if (membershipChanged.compareAndSet(true, false)) {
                stableCount = 0;
            } else {
                stableCount++;
            }
            try {
                TimeUnit.MILLISECONDS.sleep(250);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        final Member[] members = getMembers();
        final Member[] view = new Member[members.length+1];
        System.arraycopy(members, 0, view, 0, members.length);
        view[members.length] = getLocalMember(false);
        Arrays.sort(view, AbsoluteOrder.comp);
        if (Arrays.equals(view, this.view)) {
            return;
        }
        this.view = view;
        viewChange(view);
    }

    @Override
    public void stop(int svc) throws ChannelException {
        super.stop(svc);
    }

    public Member[] getView() {
        return view;
    }

    public Member getCoordinator() {
        return view == null ? null : view[0];
    }

    public boolean isCoordinator() {
        return view == null ? false : getLocalMember(false).equals(
                getCoordinator());
    }

}
