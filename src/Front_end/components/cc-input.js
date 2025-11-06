// /Users/tuanpham/Chinese-chess-nework-programming/src/Front_end/components/cc-input.js
class CCInput extends HTMLElement{
	static get observedAttributes(){ return ['type','placeholder','icon','src']; }
	constructor(){
		super();
		const root = this.attachShadow({mode:'open'});
		root.innerHTML = `
			<style>
				:host{
					display:block;
					position: relative;
					width: calc(var(--w, 400px) * var(--scale));
					margin-bottom: calc(var(--mb, 24px) * var(--scale));
				}
				.wrap{
					position: relative;
					display: flex;
					align-items: center;
					background: rgba(0, 0, 0, 0.4);
					border-radius: calc(var(--radius, 50px) * var(--scale));
					padding: calc(20px * var(--scale)) calc(24px * var(--scale));
					transition: background .2s ease;
					border: 1px solid rgba(255, 255, 255, 0.2);
				}
				.wrap:focus-within{
					background: rgba(0, 0, 0, 0.5);
					border-color: rgba(255, 255, 255, 0.4);
				}
				.icon{
					width: calc(28px * var(--scale));
					height: calc(28px * var(--scale));
					margin-right: calc(16px * var(--scale));
					flex-shrink: 0;
					opacity: 1;
					display: var(--icon-display, block);
				}
				input{
					flex: 1;
					background: transparent;
					border: none;
					outline: none;
					color: #fff;
					font-size: calc(18px * var(--scale));
					font-family: inherit;
				}
				input::placeholder{
					color: rgba(255, 255, 255, 0.7);
					font-style: italic;
				}
			</style>
			<div class="wrap">
				<img class="icon" part="icon" />
				<input type="text" part="input" />
			</div>
		`;
		this.$input = root.querySelector('input');
		this.$icon = root.querySelector('.icon');
	}
	connectedCallback(){ 
		this.#sync();
		this.$input.addEventListener('focus', this.#handleFocus);
		this.$input.addEventListener('blur', this.#handleBlur);
	}
	disconnectedCallback(){
		this.$input.removeEventListener('focus', this.#handleFocus);
		this.$input.removeEventListener('blur', this.#handleBlur);
	}
	attributeChangedCallback(){ this.#sync(); }
	#sync(){
		const type = this.getAttribute('type') || 'text';
		const placeholder = this.getAttribute('placeholder') || '';
		const iconSrc = this.getAttribute('icon') || '';
		
		this.$input.type = type === 'password' ? 'password' : 'text';
		this.$input.placeholder = placeholder;
		if(iconSrc){
			this.$icon.src = iconSrc;
			this.$icon.style.display = 'block';
			this.style.setProperty('--icon-display', 'block');
		} else {
			this.$icon.style.display = 'none';
			this.style.setProperty('--icon-display', 'none');
		}
	}
	#handleFocus = () => {
		this.$input.placeholder = '';
	}
	#handleBlur = () => {
		if(!this.$input.value){
			this.$input.placeholder = this.getAttribute('placeholder') || '';
		}
	}
	get value(){ return this.$input.value; }
	set value(v){ this.$input.value = v; }
}
customElements.define('cc-input', CCInput);