package lucee.runtime.spooler;

import lucee.commons.io.DevNullOutputStream;
import lucee.commons.io.SystemUtil.TemplateLine;
import lucee.commons.io.log.LogUtil;
import lucee.commons.lang.Pair;
import lucee.runtime.PageContext;
import lucee.runtime.config.Config;
import lucee.runtime.config.ConfigWeb;
import lucee.runtime.engine.ThreadLocalPageContext;
import lucee.runtime.exp.PageException;
import lucee.runtime.op.Caster;
import lucee.runtime.spooler.mail.MailSpoolerTask;
import lucee.runtime.thread.ThreadUtil;
import lucee.runtime.type.Struct;
import lucee.runtime.type.StructImpl;
import lucee.runtime.type.dt.DateTimeImpl;
import lucee.runtime.type.util.KeyConstants;

public abstract class CFMLSpoolerTaskListener extends SpoolerTaskListener {

	private final SpoolerTask task;
	private TemplateLine currTemplate;

	public CFMLSpoolerTaskListener(TemplateLine currTemplate, SpoolerTask task) {
		this.task = task;
		this.currTemplate = currTemplate;
	}

	@Override
	public void listen(Config config, Exception e, boolean before) {

		if (!(config instanceof ConfigWeb)) return;
		ConfigWeb cw = (ConfigWeb) config;

		PageContext pc = ThreadLocalPageContext.get();
		boolean pcCreated = false;
		if (pc == null) {
			pcCreated = true;
			Pair[] parr = new Pair[0];
			DevNullOutputStream os = DevNullOutputStream.DEV_NULL_OUTPUT_STREAM;
			pc = ThreadUtil.createDummyPageContext(cw);

			pc.setRequestTimeout(config.getRequestTimeout().getMillis());
		}
		try {
			Struct args = new StructImpl();

			long l = task.lastExecution();
			if (l > 0) args.set("lastExecution", new DateTimeImpl(pc, l, true));
			l = task.nextExecution();
			if (l > 0) args.set("nextExecution", new DateTimeImpl(pc, l, true));
			args.set("created", new DateTimeImpl(pc, task.getCreation(), true));
			args.set(KeyConstants._id, task.getId());
			args.set(KeyConstants._type, task.getType());

			Struct details = task.detail();
			if (task instanceof MailSpoolerTask) {
				details.set(KeyConstants._charset, ((MailSpoolerTask) task).getCharset());
				details.set(KeyConstants._replyto, ((MailSpoolerTask) task).getReplyTos());
				details.set("failto", ((MailSpoolerTask) task).getFailTos());
			}
			args.set(KeyConstants._detail, details);

			args.set(KeyConstants._tries, task.tries());
			args.set("remainingtries", e == null ? 0 : task.getPlans().length - task.tries());
			args.set("closed", task.closed());
			if (!before) args.set("passed", e == null);
			if (e != null) args.set("exception", Caster.toPageException(e).getCatchBlock(cw));

			Struct curr = new StructImpl();
			args.set("caller", curr);
			curr.set("template", currTemplate.template);
			curr.set("line", Double.valueOf(currTemplate.line));

			Struct adv = new StructImpl();
			args.set("advanced", adv);
			adv.set("exceptions", task.getExceptions());
			adv.set("executedPlans", task.getPlans());

			Object o = _listen(pc, args, before);
			if (before && o instanceof Struct && task instanceof MailSpoolerTask) {
				((MailSpoolerTask) task).mod((Struct) o);
			}

		}
		catch (Exception pe) {
			LogUtil.log(ThreadLocalPageContext.get(), CFMLSpoolerTaskListener.class.getName(), pe);
		}
		finally {
			if (pcCreated) ThreadLocalPageContext.release();
		}
	}

	public abstract Object _listen(PageContext pc, Struct args, boolean before) throws PageException;
}